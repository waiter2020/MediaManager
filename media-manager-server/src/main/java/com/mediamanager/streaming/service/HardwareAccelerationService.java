package com.mediamanager.streaming.service;

import com.mediamanager.streaming.dto.HardwareAccelerationProbeDto;
import com.mediamanager.streaming.dto.HardwareAccelerationType;
import com.mediamanager.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HardwareAccelerationService {

    private static final Set<String> TRACKED_ENCODERS = Set.of(
            "h264_nvenc", "h264_qsv", "h264_vaapi", "h264_amf");
    private static final int PROBE_ENCODE_TIMEOUT_SECONDS = 5;
    private static final long PROBE_CACHE_TTL_MS = 60_000;
    private static final int PROBE_ERROR_SUMMARY_MAX_CHARS = 200;
    /** AMD VA-API 编码器要求宽高均 ≥128（见 h264_vaapi constraints）。 */
    private static final String PROBE_LAVFI_INPUT = "color=c=black:s=128x128:d=0.04";

    private final SysConfigService sysConfigService;

    @Value("${mediamanager.ffmpeg.path:ffmpeg}")
    private String yamlFfmpegPath;

    @Value("${mediamanager.playback.hardware-encoder:}")
    private String yamlHardwareEncoder;

    @Value("${mediamanager.playback.hardware-acceleration:auto}")
    private String yamlHardwareAcceleration;

    @Value("${mediamanager.playback.hardware-device:/dev/dri/renderD128}")
    private String yamlHardwareDevice;

    private volatile CachedProbe cachedProbe;

    public enum QsvInitMode {
        VAAPI_BRIDGE,
        HWACCEL
    }

    public enum GpuVendor {
        INTEL,
        AMD,
        NVIDIA,
        UNKNOWN
    }

    public record ResolvedHardwareAcceleration(
            HardwareAccelerationType configuredType,
            HardwareAccelerationType resolvedType,
            String encoderName,
            String devicePath,
            QsvInitMode qsvInitMode,
            String libvaDriverName) {
        public ResolvedHardwareAcceleration(
                HardwareAccelerationType configuredType,
                HardwareAccelerationType resolvedType,
                String encoderName,
                String devicePath) {
            this(configuredType, resolvedType, encoderName, devicePath, QsvInitMode.VAAPI_BRIDGE, null);
        }

        public ResolvedHardwareAcceleration(
                HardwareAccelerationType configuredType,
                HardwareAccelerationType resolvedType,
                String encoderName,
                String devicePath,
                QsvInitMode qsvInitMode) {
            this(configuredType, resolvedType, encoderName, devicePath, qsvInitMode, null);
        }

        public boolean available() {
            return resolvedType != HardwareAccelerationType.NONE && encoderName != null && !encoderName.isBlank();
        }

        public QsvInitMode effectiveQsvInitMode() {
            return qsvInitMode != null ? qsvInitMode : QsvInitMode.VAAPI_BRIDGE;
        }

        public ResolvedHardwareAcceleration withQsvInitMode(QsvInitMode mode) {
            return new ResolvedHardwareAcceleration(
                    configuredType, resolvedType, encoderName, devicePath, mode, libvaDriverName);
        }

        public ResolvedHardwareAcceleration withLibvaDriver(String driver) {
            return new ResolvedHardwareAcceleration(
                    configuredType, resolvedType, encoderName, devicePath, qsvInitMode, driver);
        }
    }

    private record ProbeResolution(ResolvedHardwareAcceleration resolved, List<String> warnings) {
    }

    private record CachedProbe(ResolvedHardwareAcceleration resolved, long timestampMs) {
    }

    private record ProbeEncodeResult(boolean success, String errorOutput) {
    }

    public HardwareAccelerationType configuredType() {
        return HardwareAccelerationType.from(
                sysConfigService.getString("playback.hardware_acceleration", yamlHardwareAcceleration));
    }

    public String devicePath() {
        return sysConfigService.getString("playback.hardware_device", yamlHardwareDevice).trim();
    }

    public String encoderOverride() {
        String value = sysConfigService.getString("playback.hardware_encoder", yamlHardwareEncoder);
        return value == null ? "" : value.trim();
    }

    public Map<String, Boolean> detectEncoders() {
        Map<String, Boolean> available = new LinkedHashMap<>();
        TRACKED_ENCODERS.forEach(encoder -> available.put(encoder, false));
        String output = runFfmpegEncodersList();
        if (output == null) {
            return available;
        }
        for (String encoder : TRACKED_ENCODERS) {
            if (output.contains(encoder)) {
                available.put(encoder, true);
            }
        }
        return available;
    }

    public HardwareAccelerationProbeDto probe() {
        HardwareAccelerationType configured = configuredType();
        Map<String, Boolean> encoders = detectEncoders();
        List<String> warnings = new ArrayList<>();

        if (!encoders.getOrDefault("h264_nvenc", false) && commandExists("nvidia-smi")) {
            warnings.add("检测到 nvidia-smi，但 FFmpeg 未列出 h264_nvenc；请确认容器已挂载 NVIDIA 运行时。");
        }

        String configuredDevice = devicePath();
        String device = resolveAccessibleDevicePath(configuredDevice, warnings);
        GpuVendor gpuVendor = detectGpuVendor(device);
        if (gpuVendor == GpuVendor.AMD && encoders.getOrDefault("h264_qsv", false)) {
            warnings.add("检测到 AMD GPU；Intel QSV 不适用，自动模式将优先尝试 VA-API / AMF。");
        }

        if (configured != HardwareAccelerationType.NONE
                && configured != HardwareAccelerationType.AUTO) {
            ResolvedHardwareAcceleration staticResolved = resolve(configured, encoders, device);
            if (!staticResolved.available()) {
                warnings.add("当前配置的加速类型 " + configured.label() + " 不可用，硬编码将回退软编码。");
            }
        }

        ProbeResolution probeResolution = resolveWithRuntimeProbe(configured, encoders, device);
        ResolvedHardwareAcceleration resolved = probeResolution.resolved();
        warnings.addAll(probeResolution.warnings());
        cacheProbeResult(resolved);

        Map<String, Boolean> encodersByType = new LinkedHashMap<>();
        encodersByType.put("nvenc", encoders.getOrDefault("h264_nvenc", false));
        encodersByType.put("qsv", encoders.getOrDefault("h264_qsv", false));
        encodersByType.put("vaapi", encoders.getOrDefault("h264_vaapi", false));
        encodersByType.put("amf", encoders.getOrDefault("h264_amf", false));

        return HardwareAccelerationProbeDto.builder()
                .configuredType(configured.value())
                .resolvedType(resolved.resolvedType().value())
                .resolvedEncoder(resolved.encoderName())
                .devicePath(device)
                .encodersAvailable(encodersByType)
                .warnings(warnings)
                .build();
    }

    public ResolvedHardwareAcceleration resolveForTranscode() {
        CachedProbe cached = cachedProbe;
        if (cached != null && System.currentTimeMillis() - cached.timestampMs() < PROBE_CACHE_TTL_MS) {
            return cached.resolved();
        }
        Map<String, Boolean> encoders = detectEncoders();
        List<String> warnings = new ArrayList<>();
        String device = resolveAccessibleDevicePath(devicePath(), warnings);
        if (!warnings.isEmpty()) {
            warnings.forEach(message -> log.debug("Hardware device resolution: {}", message));
        }
        ProbeResolution probeResolution = resolveWithRuntimeProbe(configuredType(), encoders, device);
        ResolvedHardwareAcceleration resolved = probeResolution.resolved();
        cacheProbeResult(resolved);
        return resolved;
    }

    String resolveAccessibleDevicePath(String configured, List<String> warnings) {
        if (isVaapiDeviceAccessible(configured)) {
            return configured;
        }
        for (Path candidate : listRenderDevices()) {
            String candidatePath = candidate.toString();
            if (isVaapiDeviceAccessible(candidatePath)) {
                if (!candidatePath.equals(configured)) {
                    warnings.add("已自动选用 GPU 设备 " + candidatePath + "（配置路径 " + configured + " 不可访问）。");
                }
                return candidatePath;
            }
        }
        if (!Files.exists(Path.of(configured))) {
            warnings.add("未找到 GPU 设备路径 " + configured + "；Intel/AMD 硬编码需挂载 /dev/dri。");
        } else if (!isVaapiDeviceAccessible(configured)) {
            warnings.add(deviceInaccessibleWarning(configured));
        }
        return configured;
    }

    boolean isVaapiDeviceAccessible(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        Path device = Path.of(path);
        return Files.exists(device) && Files.isReadable(device);
    }

    List<Path> listRenderDevices() {
        Path dri = Path.of("/dev/dri");
        if (!Files.isDirectory(dri)) {
            return List.of();
        }
        try (var stream = Files.list(dri)) {
            return stream
                    .filter(path -> path.getFileName().toString().startsWith("renderD"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            log.debug("Failed to list /dev/dri render nodes", e);
            return List.of();
        }
    }

    private void cacheProbeResult(ResolvedHardwareAcceleration resolved) {
        cachedProbe = new CachedProbe(resolved, System.currentTimeMillis());
    }

    private ProbeResolution resolveWithRuntimeProbe(
            HardwareAccelerationType configured,
            Map<String, Boolean> encoders,
            String device) {
        List<String> warnings = new ArrayList<>();

        if (configured == HardwareAccelerationType.NONE) {
            return new ProbeResolution(
                    new ResolvedHardwareAcceleration(
                            HardwareAccelerationType.NONE,
                            HardwareAccelerationType.NONE,
                            null,
                            device),
                    warnings);
        }

        if (configured == HardwareAccelerationType.AUTO) {
            return resolveAutoWithRuntimeProbe(encoders, device, warnings);
        }

        ResolvedHardwareAcceleration resolved = resolve(configured, encoders, device);
        if (!resolved.available()) {
            return new ProbeResolution(resolved, warnings);
        }
        Optional<ResolvedHardwareAcceleration> probed = tryProbeEncode(resolved, warnings);
        if (probed.isPresent()) {
            return new ProbeResolution(probed.get(), warnings);
        }
        return new ProbeResolution(
                new ResolvedHardwareAcceleration(configured, HardwareAccelerationType.NONE, null, device),
                warnings);
    }

    private ProbeResolution resolveAutoWithRuntimeProbe(
            Map<String, Boolean> encoders,
            String device,
            List<String> warnings) {
        List<HardwareAccelerationType> candidates = autoCandidates(encoders, device);
        if (candidates.isEmpty()) {
            return new ProbeResolution(
                    new ResolvedHardwareAcceleration(
                            HardwareAccelerationType.AUTO,
                            HardwareAccelerationType.NONE,
                            null,
                            device),
                    warnings);
        }

        List<HardwareAccelerationType> failed = new ArrayList<>();
        for (HardwareAccelerationType type : candidates) {
            ResolvedHardwareAcceleration candidate = resolve(type, encoders, device);
            if (!candidate.available()) {
                continue;
            }
            Optional<ResolvedHardwareAcceleration> probed = tryProbeEncode(candidate, warnings);
            if (probed.isPresent()) {
                ResolvedHardwareAcceleration successful = probed.get();
                if (!failed.isEmpty()) {
                    String failedLabels = failed.stream()
                            .map(HardwareAccelerationType::label)
                            .collect(Collectors.joining("、"));
                    warnings.add(failedLabels + " 试编码失败，已改用 " + type.label() + "。");
                }
                return new ProbeResolution(
                        new ResolvedHardwareAcceleration(
                                HardwareAccelerationType.AUTO,
                                type,
                                successful.encoderName(),
                                successful.devicePath(),
                                successful.qsvInitMode(),
                                successful.libvaDriverName()),
                        warnings);
            }
            failed.add(type);
        }

        if (!failed.isEmpty()) {
            String failedLabels = failed.stream()
                    .map(HardwareAccelerationType::label)
                    .collect(Collectors.joining("、"));
            warnings.add("所有硬件加速候选（" + failedLabels
                    + "）试编码均失败；硬编码将回退软编码。详情见 data/cache/hls/*/ffmpeg-hardware.log。");
        }
        return new ProbeResolution(
                new ResolvedHardwareAcceleration(
                        HardwareAccelerationType.AUTO,
                        HardwareAccelerationType.NONE,
                        null,
                        device),
                warnings);
    }

    private Optional<ResolvedHardwareAcceleration> tryProbeEncode(
            ResolvedHardwareAcceleration hw,
            List<String> warnings) {
        if (requiresDrmDevice(hw.resolvedType()) && !isVaapiDeviceAccessible(hw.devicePath())) {
            warnings.add(deviceInaccessibleWarning(hw.devicePath()));
            return Optional.empty();
        }

        GpuVendor vendor = detectGpuVendor(hw.devicePath());
        if (hw.resolvedType() == HardwareAccelerationType.QSV) {
            if (vendor == GpuVendor.AMD) {
                warnings.add("Intel QSV 不支持 AMD GPU；请改用 VA-API 或 AMF。");
                return Optional.empty();
            }
            String lastError = null;
            for (QsvInitMode mode : QsvInitMode.values()) {
                ResolvedHardwareAcceleration candidate = hw.withQsvInitMode(mode);
                ProbeEncodeResult result = runProbeEncodeTestInternal(candidate, null);
                if (result.success()) {
                    return Optional.of(candidate);
                }
                lastError = result.errorOutput();
            }
            warnings.add(buildProbeFailureWarning(HardwareAccelerationType.QSV, lastError));
            return Optional.empty();
        }

        if (hw.resolvedType() == HardwareAccelerationType.VAAPI) {
            String lastError = null;
            for (String driver : libvaDriversToTry(vendor)) {
                String effectiveDriver = driver == null || driver.isBlank() ? null : driver;
                ResolvedHardwareAcceleration candidate = hw.withLibvaDriver(effectiveDriver);
                for (String filterChain : vaapiProbeFilterChains(vendor)) {
                    ProbeEncodeResult result = runProbeEncodeTestInternal(
                            candidate, effectiveDriver, filterChain);
                    if (result.success()) {
                        return Optional.of(candidate);
                    }
                    lastError = result.errorOutput();
                }
            }
            warnings.add(buildProbeFailureWarning(HardwareAccelerationType.VAAPI, lastError));
            return Optional.empty();
        }

        ProbeEncodeResult result = runProbeEncodeTestInternal(hw, null);
        if (result.success()) {
            return Optional.of(hw);
        }
        warnings.add(buildProbeFailureWarning(hw.resolvedType(), result.errorOutput()));
        return Optional.empty();
    }

    private boolean requiresDrmDevice(HardwareAccelerationType type) {
        return type == HardwareAccelerationType.QSV || type == HardwareAccelerationType.VAAPI;
    }

    private String deviceInaccessibleWarning(String device) {
        GpuVendor vendor = detectGpuVendor(device);
        String driverHint = switch (vendor) {
            case AMD -> "AMD 核显需 mesa-va-gallium + mesa-dri-gallium（LIBVA_DRIVER_NAME=radeonsi）";
            case INTEL -> "Intel 核显需 intel-media-driver（LIBVA_DRIVER_NAME=iHD）";
            default -> "需安装对应 GPU 的 libva 驱动";
        };
        return "GPU 设备 " + device + " 不可访问（I/O error）；请确认 compose 已挂载 /dev/dri 并加入 video/render 组，"
                + "且镜像已安装硬件驱动（" + driverHint + "）。";
    }

    GpuVendor detectGpuVendor(String devicePath) {
        if (devicePath == null || devicePath.isBlank()) {
            return GpuVendor.UNKNOWN;
        }
        String renderNode = Path.of(devicePath).getFileName().toString();
        if (!renderNode.startsWith("renderD")) {
            return GpuVendor.UNKNOWN;
        }
        Path deviceDir = Path.of("/sys/class/drm", renderNode, "device");
        GpuVendor fromPci = readPciVendor(deviceDir.resolve("vendor"));
        if (fromPci != GpuVendor.UNKNOWN) {
            return fromPci;
        }
        return readKernelDriver(deviceDir.resolve("driver"));
    }

    private GpuVendor readPciVendor(Path vendorFile) {
        try {
            if (!Files.exists(vendorFile)) {
                return GpuVendor.UNKNOWN;
            }
            String vendor = Files.readString(vendorFile).trim().toLowerCase(Locale.ROOT);
            return switch (vendor) {
                case "0x8086" -> GpuVendor.INTEL;
                case "0x1002", "0x1022" -> GpuVendor.AMD;
                case "0x10de" -> GpuVendor.NVIDIA;
                default -> GpuVendor.UNKNOWN;
            };
        } catch (IOException e) {
            log.debug("Failed to read GPU vendor from {}", vendorFile, e);
            return GpuVendor.UNKNOWN;
        }
    }

    private GpuVendor readKernelDriver(Path driverLink) {
        try {
            if (!Files.exists(driverLink)) {
                return GpuVendor.UNKNOWN;
            }
            String driver = Files.readSymbolicLink(driverLink).getFileName().toString();
            return switch (driver) {
                case "i915", "xe" -> GpuVendor.INTEL;
                case "amdgpu", "radeon" -> GpuVendor.AMD;
                case "nvidia", "nouveau" -> GpuVendor.NVIDIA;
                default -> GpuVendor.UNKNOWN;
            };
        } catch (IOException e) {
            log.debug("Failed to read GPU driver from {}", driverLink, e);
            return GpuVendor.UNKNOWN;
        }
    }

    List<String> libvaDriversToTry(GpuVendor vendor) {
        return switch (vendor) {
            case AMD -> List.of("radeonsi", "r600");
            case INTEL -> List.of("iHD", "i965");
            default -> List.of("");
        };
    }

    public void applyFfmpegHardwareEnvironment(ProcessBuilder processBuilder, ResolvedHardwareAcceleration hw) {
        String driver = hw.libvaDriverName();
        if (driver == null && requiresDrmDevice(hw.resolvedType())) {
            List<String> drivers = libvaDriversToTry(detectGpuVendor(hw.devicePath()));
            driver = drivers.isEmpty() ? null : drivers.getFirst();
        }
        if (driver != null && !driver.isBlank()) {
            processBuilder.environment().put("LIBVA_DRIVER_NAME", driver);
        }
    }

    private String buildProbeFailureWarning(HardwareAccelerationType type, String errorOutput) {
        String summary = summarizeProbeError(errorOutput);
        if (summary.isBlank()) {
            return "运行时试编码失败（" + type.label()
                    + "）；硬编码将回退软编码。详情见 data/cache/hls/*/ffmpeg-hardware.log。";
        }
        return "运行时试编码失败（" + type.label() + "）：" + summary;
    }

    private String summarizeProbeError(String errorOutput) {
        if (errorOutput == null || errorOutput.isBlank()) {
            return "";
        }
        String compact = errorOutput.replaceAll("\\s+", " ").trim();
        if (compact.length() <= PROBE_ERROR_SUMMARY_MAX_CHARS) {
            return compact;
        }
        return compact.substring(0, PROBE_ERROR_SUMMARY_MAX_CHARS) + "...";
    }

    public ResolvedHardwareAcceleration resolve(
            HardwareAccelerationType configured,
            Map<String, Boolean> encoders,
            String device) {
        String override = encoderOverride();
        if (!override.isBlank()
                && !"auto".equalsIgnoreCase(override)
                && configured != HardwareAccelerationType.AUTO) {
            return new ResolvedHardwareAcceleration(configured, configured, override, device);
        }

        HardwareAccelerationType resolved = switch (configured) {
            case NONE -> HardwareAccelerationType.NONE;
            case NVENC -> encoders.getOrDefault("h264_nvenc", false)
                    ? HardwareAccelerationType.NVENC : HardwareAccelerationType.NONE;
            case QSV -> encoders.getOrDefault("h264_qsv", false)
                    ? HardwareAccelerationType.QSV : HardwareAccelerationType.NONE;
            case VAAPI -> encoders.getOrDefault("h264_vaapi", false)
                    ? HardwareAccelerationType.VAAPI : HardwareAccelerationType.NONE;
            case AMF -> encoders.getOrDefault("h264_amf", false)
                    ? HardwareAccelerationType.AMF : HardwareAccelerationType.NONE;
            case AUTO -> autoPick(encoders, device);
        };

        String encoder = encoderForType(resolved);
        return new ResolvedHardwareAcceleration(configured, resolved, encoder, device);
    }

    private HardwareAccelerationType autoPick(Map<String, Boolean> encoders, String device) {
        List<HardwareAccelerationType> candidates = autoCandidates(encoders, device);
        return candidates.isEmpty() ? HardwareAccelerationType.NONE : candidates.getFirst();
    }

    List<HardwareAccelerationType> autoCandidates(Map<String, Boolean> encoders, String device) {
        return autoCandidates(encoders, detectGpuVendor(device));
    }

    List<HardwareAccelerationType> autoCandidates(Map<String, Boolean> encoders, GpuVendor vendor) {
        List<HardwareAccelerationType> candidates = new ArrayList<>();
        if (Boolean.TRUE.equals(encoders.get("h264_nvenc"))) {
            candidates.add(HardwareAccelerationType.NVENC);
        }
        if (vendor != GpuVendor.AMD
                && vendor != GpuVendor.NVIDIA
                && Boolean.TRUE.equals(encoders.get("h264_qsv"))) {
            candidates.add(HardwareAccelerationType.QSV);
        }
        if (Boolean.TRUE.equals(encoders.get("h264_vaapi"))) {
            candidates.add(HardwareAccelerationType.VAAPI);
        }
        if (Boolean.TRUE.equals(encoders.get("h264_amf"))) {
            candidates.add(HardwareAccelerationType.AMF);
        }
        return candidates;
    }

    public String encoderForType(HardwareAccelerationType type) {
        return switch (type) {
            case NVENC -> "h264_nvenc";
            case QSV -> "h264_qsv";
            case VAAPI -> "h264_vaapi";
            case AMF -> "h264_amf";
            case NONE, AUTO -> null;
        };
    }

    public void appendHardwareInputArgs(List<String> command, ResolvedHardwareAcceleration hw) {
        if (!hw.available()) {
            throw new IllegalStateException("Hardware acceleration is not available");
        }

        String override = encoderOverride();
        if (!override.isBlank()
                && !TRACKED_ENCODERS.contains(override)
                && hw.configuredType() != HardwareAccelerationType.AUTO) {
            return;
        }

        switch (hw.resolvedType()) {
            case VAAPI -> appendVaapiInputArgs(command, hw.devicePath());
            case QSV -> appendQsvInputArgs(command, hw);
            default -> {
            }
        }
    }

    public void appendHardwareOutputArgs(
            List<String> command,
            ResolvedHardwareAcceleration hw,
            String scaleFilter,
            int bitrateKbps) {
        appendHardwareEncodeArgs(command, hw, scaleFilter, bitrateKbps);
    }

    public void appendHardwareEncodeArgs(
            List<String> command,
            ResolvedHardwareAcceleration hw,
            String scaleFilter,
            int bitrateKbps) {
        appendHardwareEncodeArgs(command, hw, scaleFilter, bitrateKbps, false);
    }

    private void appendHardwareEncodeArgs(
            List<String> command,
            ResolvedHardwareAcceleration hw,
            String scaleFilter,
            int bitrateKbps,
            boolean probeEncode) {
        if (!hw.available()) {
            throw new IllegalStateException("Hardware acceleration is not available");
        }

        String override = encoderOverride();
        if (!override.isBlank()
                && !TRACKED_ENCODERS.contains(override)
                && hw.configuredType() != HardwareAccelerationType.AUTO) {
            applySimpleHardwareEncoder(command, override, scaleFilter, bitrateKbps);
            return;
        }

        switch (hw.resolvedType()) {
            case NVENC -> appendNvencOutputArgs(command, scaleFilter, bitrateKbps);
            case VAAPI -> appendVaapiOutputArgs(command, hw, scaleFilter, bitrateKbps, probeEncode);
            case QSV -> appendQsvOutputArgs(command, hw, scaleFilter, bitrateKbps);
            case AMF -> appendAmfOutputArgs(command, scaleFilter, bitrateKbps);
            default -> applySimpleHardwareEncoder(command, hw.encoderName(), scaleFilter, bitrateKbps);
        }
    }

    private void appendVaapiOutputArgs(
            List<String> command,
            ResolvedHardwareAcceleration hw,
            String scaleFilter,
            int bitrateKbps,
            boolean probeEncode,
            String filterChainOverride) {
        String vf = filterChainOverride != null
                ? filterChainOverride
                : buildVaapiFilterChain(scaleFilter, hw.devicePath());
        command.addAll(List.of("-vf", vf));
        command.addAll(List.of("-c:v", "h264_vaapi", "-profile:v", "main"));
        if (detectGpuVendor(hw.devicePath()) == GpuVendor.INTEL) {
            command.add("-level");
            command.add("4.1");
        }
        if (probeEncode) {
            command.addAll(List.of("-qp", "26"));
        } else {
            command.addAll(videoBitrateArgs(bitrateKbps));
        }
    }

    private void appendVaapiInputArgs(List<String> command, String device) {
        command.addAll(List.of(
                "-init_hw_device", "vaapi=va:" + device,
                "-filter_hw_device", "va"));
    }

    private void appendQsvInputArgs(List<String> command, ResolvedHardwareAcceleration hw) {
        if (hw.effectiveQsvInitMode() == QsvInitMode.HWACCEL) {
            command.addAll(List.of("-hwaccel", "qsv", "-qsv_device", hw.devicePath()));
            return;
        }
        command.addAll(List.of(
                "-init_hw_device", "vaapi=va:" + hw.devicePath(),
                "-init_hw_device", "qsv=hw@va",
                "-filter_hw_device", "hw"));
    }

    private void appendNvencOutputArgs(List<String> command, String scaleFilter, int bitrateKbps) {
        if (scaleFilter != null) {
            command.addAll(List.of("-vf", scaleFilter));
        }
        command.addAll(List.of("-c:v", "h264_nvenc", "-preset", "p4", "-pix_fmt", "yuv420p"));
        command.addAll(videoBitrateArgs(bitrateKbps));
    }

    private void appendVaapiOutputArgs(
            List<String> command,
            ResolvedHardwareAcceleration hw,
            String scaleFilter,
            int bitrateKbps,
            boolean probeEncode) {
        appendVaapiOutputArgs(command, hw, scaleFilter, bitrateKbps, probeEncode, null);
    }

    private List<String> vaapiProbeFilterChains(GpuVendor vendor) {
        if (vendor == GpuVendor.INTEL) {
            return List.of(
                    "format=nv12,hwupload=derive_device=va",
                    "format=nv12,hwupload");
        }
        // AMD 上 derive_device=va 会触发驱动层 "Cannot allocate memory"（FFmpeg #11618）
        return List.of(
                "format=nv12,hwupload",
                "format=nv12,hwupload=extra_hw_frames=4");
    }

    private String vaapiHwuploadFilter(String devicePath) {
        if (detectGpuVendor(devicePath) == GpuVendor.INTEL) {
            return "hwupload=derive_device=va";
        }
        return "hwupload";
    }

    private void appendQsvOutputArgs(
            List<String> command,
            ResolvedHardwareAcceleration hw,
            String scaleFilter,
            int bitrateKbps) {
        if (hw.effectiveQsvInitMode() == QsvInitMode.HWACCEL) {
            if (scaleFilter != null) {
                command.addAll(List.of("-vf", scaleFilter));
            }
        } else {
            String vf = buildQsvFilterChain(scaleFilter);
            command.addAll(List.of("-vf", vf));
        }
        command.addAll(List.of("-c:v", "h264_qsv", "-preset", "fast", "-profile:v", "main"));
        command.addAll(videoBitrateArgs(bitrateKbps));
    }

    private void appendAmfOutputArgs(List<String> command, String scaleFilter, int bitrateKbps) {
        if (scaleFilter != null) {
            command.addAll(List.of("-vf", scaleFilter));
        }
        command.addAll(List.of("-c:v", "h264_amf", "-quality", "speed", "-pix_fmt", "yuv420p"));
        command.addAll(videoBitrateArgs(bitrateKbps));
    }

    private String buildQsvFilterChain(String scaleFilter) {
        StringBuilder chain = new StringBuilder("hwupload=extra_hw_frames=64");
        if (scaleFilter != null && !scaleFilter.isBlank()) {
            chain.append(',').append(toQsvScaleFilter(scaleFilter));
        }
        chain.append(",format=qsv");
        return chain.toString();
    }

    private String buildVaapiFilterChain(String scaleFilter, String devicePath) {
        StringBuilder chain = new StringBuilder("format=nv12,").append(vaapiHwuploadFilter(devicePath));
        if (scaleFilter != null && !scaleFilter.isBlank()) {
            chain.append(',').append(toVaapiScaleFilter(scaleFilter));
        }
        return chain.toString();
    }

    private String toQsvScaleFilter(String scaleFilter) {
        if (scaleFilter.startsWith("scale=")) {
            return "scale_qsv=" + scaleFilter.substring("scale=".length());
        }
        return scaleFilter.replace("scale", "scale_qsv");
    }

    private String toVaapiScaleFilter(String scaleFilter) {
        if (scaleFilter.startsWith("scale=")) {
            return "scale_vaapi=" + scaleFilter.substring("scale=".length());
        }
        return scaleFilter.replace("scale", "scale_vaapi");
    }

    private void applySimpleHardwareEncoder(
            List<String> command,
            String encoder,
            String scaleFilter,
            int bitrateKbps) {
        if (scaleFilter != null) {
            command.addAll(List.of("-vf", scaleFilter));
        }
        command.addAll(List.of("-c:v", encoder, "-preset", "fast", "-pix_fmt", "yuv420p"));
        command.addAll(videoBitrateArgs(bitrateKbps));
    }

    private List<String> videoBitrateArgs(int bitrateKbps) {
        return List.of(
                "-b:v", bitrateKbps + "k",
                "-maxrate", Math.round(bitrateKbps * 1.25f) + "k",
                "-bufsize", Math.round(bitrateKbps * 2.0f) + "k");
    }

    private ProbeEncodeResult runProbeEncodeTestInternal(ResolvedHardwareAcceleration hw, String libvaDriver) {
        return runProbeEncodeTestInternal(hw, libvaDriver, null);
    }

    private ProbeEncodeResult runProbeEncodeTestInternal(
            ResolvedHardwareAcceleration hw,
            String libvaDriver,
            String vaapiFilterChainOverride) {
        List<String> command = new ArrayList<>();
        command.add(sysConfigService.ffmpegPath(yamlFfmpegPath));
        command.addAll(List.of("-hide_banner", "-loglevel", "error", "-y"));
        appendHardwareInputArgs(command, hw);
        command.addAll(List.of(
                "-f", "lavfi",
                "-i", PROBE_LAVFI_INPUT,
                "-frames:v", "1",
                "-an"));
        if (hw.resolvedType() == HardwareAccelerationType.VAAPI && vaapiFilterChainOverride != null) {
            appendVaapiOutputArgs(command, hw, null, 500, true, vaapiFilterChainOverride);
        } else {
            appendHardwareEncodeArgs(command, hw, null, 500, true);
        }
        command.addAll(List.of("-f", "null", "-"));

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command).redirectErrorStream(true);
            applyFfmpegHardwareEnvironment(processBuilder, libvaDriver != null
                    ? hw.withLibvaDriver(libvaDriver)
                    : hw);
            Process process = processBuilder.start();
            boolean finished = process.waitFor(PROBE_ENCODE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.debug("Hardware probe encode timed out for {} ({})",
                        hw.resolvedType(), hw.effectiveQsvInitMode());
                return new ProbeEncodeResult(false, "probe encode timed out");
            }
            String output = new String(process.getInputStream().readAllBytes());
            if (process.exitValue() != 0) {
                log.debug("Hardware probe encode failed for {} ({}): exit={}, output={}",
                        hw.resolvedType(), hw.effectiveQsvInitMode(), process.exitValue(), output);
                return new ProbeEncodeResult(false, output);
            }
            return new ProbeEncodeResult(true, "");
        } catch (Exception e) {
            log.debug("Hardware probe encode failed for {} ({})", hw.resolvedType(), hw.effectiveQsvInitMode(), e);
            return new ProbeEncodeResult(false, e.getMessage());
        }
    }

    private String runFfmpegEncodersList() {
        try {
            Process process = new ProcessBuilder(sysConfigService.ffmpegPath(yamlFfmpegPath), "-hide_banner", "-encoders")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished || process.exitValue() != 0) {
                return null;
            }
            return new String(process.getInputStream().readAllBytes());
        } catch (Exception e) {
            log.debug("Failed to list FFmpeg encoders", e);
            return null;
        }
    }

    private boolean commandExists(String command) {
        try {
            Process process = new ProcessBuilder(command, "--version")
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
