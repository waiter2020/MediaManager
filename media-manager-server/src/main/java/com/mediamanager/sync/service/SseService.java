package com.mediamanager.sync.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SseService {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter createEmitter(String clientId) {
        // Timeout 30 minutes
        SseEmitter emitter = new SseEmitter(30L * 60 * 1000);
        
        emitter.onCompletion(() -> {
            log.debug("SSE Emitter completed for client: {}", clientId);
            emitters.remove(clientId);
        });
        
        emitter.onTimeout(() -> {
            log.debug("SSE Emitter timed out for client: {}", clientId);
            emitter.complete();
            emitters.remove(clientId);
        });
        
        emitter.onError((e) -> {
            if (isClientDisconnect(e)) {
                log.debug("SSE client disconnected: {}", clientId);
            } else {
                log.debug("SSE Emitter error for client: {}", clientId, e);
            }
            emitters.remove(clientId);
        });

        emitters.put(clientId, emitter);
        log.info("SSE client connected: {}. Total connected: {}", clientId, emitters.size());
        
        return emitter;
    }

    public void broadcast(String eventName, Object payload) {
        log.debug("Broadcasting SSE Event: {}", eventName);
        emitters.forEach((clientId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException e) {
                if (isClientDisconnect(e)) {
                    log.debug("SSE client disconnected while sending: {}", clientId);
                } else {
                    log.debug("Failed to send SSE event to client: {}", clientId, e);
                }
                emitters.remove(clientId);
            }
        });
    }

    public void sendToUser(String clientId, String eventName, Object payload) {
        SseEmitter emitter = emitters.get(clientId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException e) {
                if (isClientDisconnect(e)) {
                    log.debug("SSE client disconnected while sending: {}", clientId);
                } else {
                    log.debug("Failed to send targeted SSE event to client: {}", clientId, e);
                }
                emitters.remove(clientId);
            }
        }
    }

    private boolean isClientDisconnect(Throwable e) {
        if (e == null) return false;
        if (e instanceof AsyncRequestNotUsableException) return true;
        Throwable cur = e;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null && msg.toLowerCase().contains("broken pipe")) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }
}
