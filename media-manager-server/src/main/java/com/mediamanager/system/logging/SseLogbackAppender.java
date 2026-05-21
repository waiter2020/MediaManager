package com.mediamanager.system.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import com.mediamanager.system.dto.SystemLogEventDto;
import com.mediamanager.system.service.SystemLogBroadcaster;

public class SseLogbackAppender extends AppenderBase<ILoggingEvent> {

    private Level threshold = Level.INFO;

    public void setThreshold(String level) {
        this.threshold = Level.toLevel(level, Level.INFO);
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (!isStarted()) {
            return;
        }
        if (eventObject.getLevel().toInt() < threshold.toInt()) {
            return;
        }

        String exceptionShort = null;
        IThrowableProxy throwableProxy = eventObject.getThrowableProxy();
        if (throwableProxy != null) {
            exceptionShort = throwableProxy.getClassName() + ": " + throwableProxy.getMessage();
        }

        SystemLogEventDto dto = SystemLogEventDto.builder()
                .timestamp(eventObject.getTimeStamp())
                .level(eventObject.getLevel().toString())
                .source("APP")
                .logger(eventObject.getLoggerName())
                .message(eventObject.getFormattedMessage())
                .thread(eventObject.getThreadName())
                .exceptionShort(exceptionShort)
                .build();

        SystemLogBroadcaster.getInstance().broadcast(dto);
    }
}

