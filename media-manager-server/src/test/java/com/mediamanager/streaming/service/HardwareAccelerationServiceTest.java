package com.mediamanager.streaming.service;

import com.mediamanager.streaming.dto.HardwareAccelerationType;
import com.mediamanager.system.service.SysConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HardwareAccelerationServiceTest {

    @Mock
    private SysConfigService sysConfigService;

    @InjectMocks
    private HardwareAccelerationService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "yamlFfmpegPath", "ffmpeg");
        ReflectionTestUtils.setField(service, "yamlHardwareEncoder", "h264_nvenc");
        ReflectionTestUtils.setField(service, "yamlHardwareAcceleration", "auto");
        ReflectionTestUtils.setField(service, "yamlHardwareDevice", "/dev/dri/renderD128");
    }

    @Test
    void autoResolvesToNvencWhenAvailable() {
        Map<String, Boolean> encoders = baselineEncoders();
        encoders.put("h264_nvenc", true);
        encoders.put("h264_qsv", true);

        var resolved = service.resolve(HardwareAccelerationType.AUTO, encoders, "/dev/dri/renderD128");

        assertThat(resolved.resolvedType()).isEqualTo(HardwareAccelerationType.NVENC);
        assertThat(resolved.encoderName()).isEqualTo("h264_nvenc");
        assertThat(resolved.available()).isTrue();
    }

    @Test
    void explicitQsvModeResolvesToQsvEvenWhenVaapiAvailable() {
        Map<String, Boolean> encoders = baselineEncoders();
        encoders.put("h264_qsv", true);
        encoders.put("h264_vaapi", true);

        var resolved = service.resolve(HardwareAccelerationType.QSV, encoders, "/dev/dri/renderD128");

        assertThat(resolved.resolvedType()).isEqualTo(HardwareAccelerationType.QSV);
        assertThat(resolved.encoderName()).isEqualTo("h264_qsv");
    }

    @Test
    void explicitVaapiModeResolvesToVaapi() {
        Map<String, Boolean> encoders = baselineEncoders();
        encoders.put("h264_qsv", true);
        encoders.put("h264_vaapi", true);

        var resolved = service.resolve(HardwareAccelerationType.VAAPI, encoders, "/dev/dri/renderD128");

        assertThat(resolved.resolvedType()).isEqualTo(HardwareAccelerationType.VAAPI);
        assertThat(resolved.encoderName()).isEqualTo("h264_vaapi");
    }

    @Test
    void autoFallsBackToNoneWhenNoEncodersAvailable() {
        var resolved = service.resolve(HardwareAccelerationType.AUTO, baselineEncoders(), "/dev/dri/renderD128");

        assertThat(resolved.resolvedType()).isEqualTo(HardwareAccelerationType.NONE);
        assertThat(resolved.available()).isFalse();
    }

    @Test
    void configuredNvencUnavailableResolvesToNone() {
        var resolved = service.resolve(HardwareAccelerationType.NVENC, baselineEncoders(), "/dev/dri/renderD128");

        assertThat(resolved.resolvedType()).isEqualTo(HardwareAccelerationType.NONE);
        assertThat(resolved.available()).isFalse();
    }

    @Test
    void encoderOverrideIgnoredForAutoMode() {
        when(sysConfigService.getString("playback.hardware_encoder", "h264_nvenc")).thenReturn("custom_encoder");

        var resolved = service.resolve(HardwareAccelerationType.AUTO, baselineEncoders(), "/dev/dri/renderD128");

        assertThat(resolved.resolvedType()).isEqualTo(HardwareAccelerationType.NONE);
        assertThat(resolved.encoderName()).isNull();
    }

    @Test
    void encoderOverrideUsedForExplicitNvencMode() {
        when(sysConfigService.getString("playback.hardware_encoder", "h264_nvenc")).thenReturn("custom_encoder");

        var resolved = service.resolve(HardwareAccelerationType.NVENC, baselineEncoders(), "/dev/dri/renderD128");

        assertThat(resolved.encoderName()).isEqualTo("custom_encoder");
    }

    @Test
    void appendQsvInputArgsUsesVaapiBridge() {
        Map<String, Boolean> encoders = baselineEncoders();
        encoders.put("h264_qsv", true);
        var hw = service.resolve(HardwareAccelerationType.QSV, encoders, "/dev/dri/renderD128");

        List<String> inputArgs = new ArrayList<>();
        service.appendHardwareInputArgs(inputArgs, hw);

        assertThat(inputArgs).containsExactly(
                "-init_hw_device", "vaapi=va:/dev/dri/renderD128",
                "-init_hw_device", "qsv=hw@va",
                "-filter_hw_device", "hw");
    }

    @Test
    void appendQsvOutputArgsBuildsExpectedFilterChain() {
        Map<String, Boolean> encoders = baselineEncoders();
        encoders.put("h264_qsv", true);
        var hw = service.resolve(HardwareAccelerationType.QSV, encoders, "/dev/dri/renderD128");

        List<String> outputArgs = new ArrayList<>();
        service.appendHardwareOutputArgs(outputArgs, hw, "scale=-2:720", 4000);

        assertThat(outputArgs).contains("-vf", "hwupload=extra_hw_frames=64,scale_qsv=-2:720,format=qsv");
        assertThat(outputArgs).contains("-c:v", "h264_qsv", "-preset", "fast", "-profile:v", "main");
        assertThat(outputArgs).contains("-b:v", "4000k");
    }

    @Test
    void appendVaapiInputArgsInitializesDeviceBeforeInput() {
        Map<String, Boolean> encoders = baselineEncoders();
        encoders.put("h264_vaapi", true);
        var hw = service.resolve(HardwareAccelerationType.VAAPI, encoders, "/dev/dri/renderD128");

        List<String> inputArgs = new ArrayList<>();
        service.appendHardwareInputArgs(inputArgs, hw);

        assertThat(inputArgs).containsExactly(
                "-init_hw_device", "vaapi=va:/dev/dri/renderD128",
                "-filter_hw_device", "va");
    }

    @Test
    void appendVaapiOutputArgsUsesHwuploadAndScaleVaapi() {
        Map<String, Boolean> encoders = baselineEncoders();
        encoders.put("h264_vaapi", true);
        var hw = service.resolve(HardwareAccelerationType.VAAPI, encoders, "/dev/dri/renderD128");

        List<String> outputArgs = new ArrayList<>();
        service.appendHardwareOutputArgs(outputArgs, hw, "scale=-2:720", 3000);

        assertThat(outputArgs).contains("-vf", "format=nv12,hwupload,scale_vaapi=-2:720");
        assertThat(outputArgs).contains("-c:v", "h264_vaapi");
        assertThat(String.join(" ", outputArgs)).doesNotContain("-hwaccel");
    }

    @Test
    void appendNvencArgsBuildsExpectedCommand() {
        Map<String, Boolean> encoders = baselineEncoders();
        encoders.put("h264_nvenc", true);
        var hw = service.resolve(HardwareAccelerationType.NVENC, encoders, "/dev/dri/renderD128");

        List<String> command = new ArrayList<>();
        service.appendHardwareOutputArgs(command, hw, "scale=1280:720", 4000);

        assertThat(command).contains("-c:v", "h264_nvenc", "-preset", "p4", "-vf", "scale=1280:720");
        assertThat(command).contains("-b:v", "4000k");
    }

    private static Map<String, Boolean> baselineEncoders() {
        Map<String, Boolean> encoders = new LinkedHashMap<>();
        encoders.put("h264_nvenc", false);
        encoders.put("h264_qsv", false);
        encoders.put("h264_vaapi", false);
        encoders.put("h264_amf", false);
        return encoders;
    }
}
