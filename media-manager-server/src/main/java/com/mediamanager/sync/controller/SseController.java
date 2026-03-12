package com.mediamanager.sync.controller;

import com.mediamanager.sync.service.SseService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sse")
@RequiredArgsConstructor
public class SseController {

    private final SseService sseService;
    
    // Custom handling for tokens here can be tricky with EventSource in browsers if not passed in query params
    // Using a clientId mapping approach to attach standard auth if needed.
    // For simplicity right now, accepting requests assuming it's behind secure proxy or using query tokens.
    
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Connect to SSE Event Stream")
    public SseEmitter streamEvents(@RequestParam(required = false) String clientId) {
        String id = clientId != null && !clientId.isEmpty() ? clientId : UUID.randomUUID().toString();
        return sseService.createEmitter(id);
    }
}
