package com.mediamanager.system.service;

import com.mediamanager.system.dto.SystemLogEventDto;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SystemLogBroadcaster {

    private static final int MAX_RECENT_EVENTS = 500;
    private static final long SSE_TIMEOUT = 30L * 60 * 1000;

    private static final SystemLogBroadcaster INSTANCE = new SystemLogBroadcaster();

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final Deque<SystemLogEventDto> recentEvents = new ArrayDeque<>(MAX_RECENT_EVENTS);

    private SystemLogBroadcaster() {
    }

    public static SystemLogBroadcaster getInstance() {
        return INSTANCE;
    }

    public synchronized void addRecentEvent(SystemLogEventDto event) {
        if (recentEvents.size() >= MAX_RECENT_EVENTS) {
            recentEvents.removeFirst();
        }
        recentEvents.addLast(event);
    }

    public synchronized List<SystemLogEventDto> getRecentEventsSnapshot() {
        return new ArrayList<>(recentEvents);
    }

    public SseEmitter registerEmitter() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((ex) -> emitters.remove(emitter));

        // send snapshot of recent events
        try {
            for (SystemLogEventDto event : getRecentEventsSnapshot()) {
                emitter.send(SseEmitter.event().name("log").data(event));
            }
        } catch (IOException e) {
            emitters.remove(emitter);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    public void broadcast(SystemLogEventDto event) {
        addRecentEvent(event);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("log").data(event));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }
}

