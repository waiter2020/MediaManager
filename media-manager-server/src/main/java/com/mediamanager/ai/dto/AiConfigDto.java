package com.mediamanager.ai.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiConfigDto {
    private String defaultProvider;
    private String llmProvider;
    private String embedProvider;
    private String ollamaBaseUrl;
    private String openaiBaseUrl;
    private String openaiApiKey;
    private String openaiLlmBaseUrl;
    private String openaiLlmApiKey;
    private String openaiEmbedBaseUrl;
    private String openaiEmbedApiKey;
    private String llmModel;
    private String embedModel;
    private Boolean classifierEnabled;
    private Boolean outboundAllowed;
    private Integer timeoutMs;
    private Boolean autoApproveEnabled;
    private Double autoApproveConfidenceThreshold;
    private String autoApproveFields;
}
