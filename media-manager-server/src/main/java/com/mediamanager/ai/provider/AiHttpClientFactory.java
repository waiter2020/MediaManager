package com.mediamanager.ai.provider;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class AiHttpClientFactory {

    public static final long DEFAULT_TIMEOUT_MS = 600_000L;
    public static final long MIN_TIMEOUT_MS = 5_000L;
    public static final long MAX_TIMEOUT_MS = 600_000L;
    private static final int CONNECT_TIMEOUT_MS = 10_000;

    public RestTemplate create(long requestedTimeoutMs) {
        int readTimeoutMs = (int) Math.max(
                MIN_TIMEOUT_MS,
                Math.min(MAX_TIMEOUT_MS, requestedTimeoutMs));
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.min(CONNECT_TIMEOUT_MS, readTimeoutMs));
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }
}
