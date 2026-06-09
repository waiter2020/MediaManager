package com.mediamanager.streaming.service;

import com.mediamanager.streaming.dto.HardwareAccelerationProbeDto;
import com.mediamanager.streaming.dto.HardwareAccelerationType;
import com.mediamanager.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class HardwareAccelerationService {

    private static final Set<String> TRACKED_ENCODERS = Set.of(
            "h264_nvenc", "h264_qsv", "h264_vaapi", "h264_amf");
    private static final int PROBE_ENCODE_TIMEOUT_SECONDS = 5;

    private final SysConfigService sysConfigService;

    @Value("${mediamanager.ffmpeg.path:ffmpeg}")
    private String yamlFfmpegPath;

    @Value("${mediamanager.playback.hardware-encoder:h264_nvenc}")
    private String yamlHardwareEncoder;

    @Value("${mediamanager.playback.hardware-acceleration:auto}")
    private String yamlHardwareAcceleration;

    @Value("${mediamanager.playback.hardware-device:/dev/dri/renderD128}")
    private String yamlHardwareDevice;

    public record ResolvedHardwareAcceleration(
            HardwareAccelerationType configuredType,
            HardwareAccelerationType resolvedType,
            String encoderName,
            String devicePath) {
        public boolean available() {
            return resolvedType != HardwareAccelerationType.NONE && encoderName != null && !encoderName.isBlank();
        }
    }

    public HardwareAccelerationType configuredType() {
        return HardwareAccelerationType.from(
                sysConfigService.getString("playback.hardware_acceleration", yamlHardwareAcceleration));
    }

    public String devicePath() {
        return sysConfigService.getString("playback.hardware_device", yamlHardwareDevice).trim();
    }

    public String encoderOverride() {
        return sysConfigService.getString("playback.hardware_encoder", yamlHardwareEncoder).trim();
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
        String device = devicePath();
        List<String> warnings = new ArrayList<>();

        if (!encoders.getOrDefault("h264_nvenc", false) && commandExists("nvidia-smi")) {
            warnings.add("检测到 nvidia-smi，但 FFmpeg 未列出 h264_nvenc；请确认容器已挂载 NVIDIA 运行时。");
        }

        String devicePath = device;
        if (configured == HardwareAccelerationType.VAAPI || configured == HardwareAccelerationType.QSV
                || configured == HardwareAccelerationType.AUTO) {
            if (!Files.exists(Path.of(device))) {
                warnings.add("未找到 GPU 设备路径 " + device + "；Intel/AMD 硬编码需挂载 /dev/dri。");
            }
        }

        ResolvedHardwareAcceleration resolved = resolve(configured, encoders, device);
        if (configured != HardwareAccelerationType.NONE
                && configured != HardwareAccelerationType.AUTO
                && !resolved.available()) {
            warnings.add("当前配置的加速类型 " + configured.label() + " 不可用，硬编码将回退软编码。");
        }

        if (resolved.available() && !runProbeEncodeTest(resolved)) {
            warnings.add("运行时试编码失败（" + resolved.resolvedType().label()
                    + "）；硬编码将回退软编码。详情见 data/cache/hls/*/ffmpeg-hardware.log。");
            resolved = new ResolvedHardwareAcceleration(
                    configured, HardwareAccelerationType.NONE, null, devicePath);
        }

        Map<String, Boolean> encodersByType = new LinkedHashMap<>();
        encodersByType.put("nvenc", encoders.getOrDefault("h264_nvenc", false));
        encodersByType.put("qsv", encoders.getOrDefault("h264_qsv", false));
        encodersByType.put("vaapi", encoders.getOrDefault("h264_vaapi", false));
        encodersByType.put("amf", encoders.getOrDefault("h264_amf", false));

        return HardwareAccelerationProbeDto.builder()
                .configuredType(configured.value())
                .resolvedType(resolved.resolvedType().value())
                .resolvedEncoder(resolved.encoderName())
                .devicePath(devicePath)
                .encodersAvailable(encodersByType)
                .warnings(warnings)
                .build();
    }

    public ResolvedHardwareAcceleration resolveForTranscode() {
        return resolve(configuredType(), detectEncoders(), devicePath());
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
            case AUTO -> autoPick(encoders);
        };

        String encoder = encoderForType(resolved);
        return new ResolvedHardwareAcceleration(configured, resolved, encoder, device);
    }

    private HardwareAccelerationType autoPick(Map<String, Boolean> encoders) {
        if (Boolean.TRUE.equals(encoders.get("h264_nvenc"))) {
            return HardwareAccelerationType.NVENC;
        }
        if (isLinuxLike()) {
            if (Boolean.TRUE.equals(encoders.get("h264_vaapi"))) {
                return HardwareAccelerationType.VAAPI;
            }
            if (Boolean.TRUE.equals(encoders.get("h264_qsv"))) {
                return HardwareAccelerationType.QSV;
            }
        } else {
            if (Boolean.TRUE.equals(encoders.get("h264_qsv"))) {
                return HardwareAccelerationType.QSV;
            }
            if (Boolean.TRUE.equals(encoders.get("h264_vaapi"))) {
                return HardwareAccelerationType.VAAPI;
            }
        }
        if (Boolean.TRUE.equals(encoders.get("h264_amf"))) {
            return HardwareAccelerationType.AMF;
        }
        return HardwareAccelerationType.NONE;
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
            case QSV -> appendQsvInputArgs(command, hw.devicePath());
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
            case VAAPI -> appendVaapiOutputArgs(command, scaleFilter, bitrateKbps);
            case QSV -> appendQsvOutputArgs(command, scaleFilter, bitrateKbps);
            case AMF -> appendAmfOutputArgs(command, scaleFilter, bitrateKbps);
            default -> applySimpleHardwareEncoder(command, hw.encoderName(), scaleFilter, bitrateKbps);
        }
    }

    private void appendVaapiInputArgs(List<String> command, String device) {
        command.addAll(List.of(
                "-init_hw_device", "vaapi=va:" + device,
                "-filter_hw_device", "va"));
    }

    private void appendQsvInputArgs(List<String> command, String device) {
        command.addAll(List.of(
                "-init_hw_device", "vaapi=va:" + device,
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

    private void appendVaapiOutputArgs(List<String> command, String scaleFilter, int bitrateKbps) {
        String vf = buildVaapiFilterChain(scaleFilter);
        command.addAll(List.of("-vf", vf));
        command.addAll(List.of("-c:v", "h264_vaapi", "-profile:v", "main", "-level", "4.1"));
        command.addAll(videoBitrateArgs(bitrateKbps));
    }

    private void appendQsvOutputArgs(List<String> command, String scaleFilter, int bitrateKbps) {
        String vf = buildQsvFilterChain(scaleFilter);
        command.addAll(List.of("-vf", vf));
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

    private String buildVaapiFilterChain(String scaleFilter) {
        StringBuilder chain = new StringBuilder("format=nv12,hwupload");
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

    private boolean runProbeEncodeTest(ResolvedHardwareAcceleration hw) {
        List<String> command = new ArrayList<>();
        command.add(sysConfigService.ffmpegPath(yamlFfmpegPath));
        command.addAll(List.of("-hide_banner", "-loglevel", "error", "-y"));
        appendHardwareInputArgs(command, hw);
        command.addAll(List.of(
                "-f", "lavfi",
                "-i", "color=c=black:s=64x64:d=0.04",
                "-frames:v", "1",
                "-an"));
        appendHardwareOutputArgs(command, hw, null, 500);
        command.addAll(List.of("-f", "null", "-"));

        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(PROBE_ENCODE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.debug("Hardware probe encode timed out for {}", hw.resolvedType());
                return false;
            }
            if (process.exitValue() != 0) {
                String output = new String(process.getInputStream().readAllBytes());
                log.debug("Hardware probe encode failed for {}: exit={}, output={}",
                        hw.resolvedType(), process.exitValue(), output);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.debug("Hardware probe encode failed for {}", hw.resolvedType(), e);
            return false;
        }
    }

    private boolean isLinuxLike() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("linux") || os.contains("unix");
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
