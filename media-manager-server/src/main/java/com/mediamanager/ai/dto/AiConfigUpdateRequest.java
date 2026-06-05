package com.mediamanager.ai.dto;

import lombok.Data;
import jakarta.validation.constraints.*;

@Data
public class AiConfigUpdateRequest {
    @Size(max = 64, message = "Provider ID cannot exceed 64 characters")
    private String defaultProvider;

    @Size(max = 64, message = "LLM provider ID cannot exceed 64 characters")
    private String llmProvider;

    @Size(max = 64, message = "Embedding provider ID cannot exceed 64 characters")
    private String embedProvider;

    @Size(max = 255, message = "Ollama URL cannot exceed 255 characters")
    @Pattern(regexp = "^(https?://.*)?$", message = "Ollama base URL must start with http:// or https://")
    private String ollamaBaseUrl;

    @Size(max = 255, message = "OpenAI URL cannot exceed 255 characters")
    @Pattern(regexp = "^(https?://.*)?$", message = "OpenAI base URL must start with http:// or https://")
    private String openaiBaseUrl;

    @Size(max = 255, message = "OpenAI API Key cannot exceed 255 characters")
    private String openaiApiKey;

    @Size(max = 255, message = "OpenAI LLM URL cannot exceed 255 characters")
    @Pattern(regexp = "^(https?://.*)?$", message = "OpenAI LLM base URL must start with http:// or https://")
    private String openaiLlmBaseUrl;

    @Size(max = 255, message = "OpenAI LLM API Key cannot exceed 255 characters")
    private String openaiLlmApiKey;

    @Size(max = 255, message = "OpenAI embedding URL cannot exceed 255 characters")
    @Pattern(regexp = "^(https?://.*)?$", message = "OpenAI embedding base URL must start with http:// or https://")
    private String openaiEmbedBaseUrl;

    @Size(max = 255, message = "OpenAI embedding API Key cannot exceed 255 characters")
    private String openaiEmbedApiKey;

    @Size(max = 64, message = "LLM Model name cannot exceed 64 characters")
    private String llmModel;

    @Size(max = 64, message = "Embedding Model name cannot exceed 64 characters")
    private String embedModel;

    private Boolean classifierEnabled;
    private Boolean outboundAllowed;

    @Min(value = 100, message = "Timeout must be at least 100ms")
    @Max(value = 600000, message = "Timeout cannot exceed 600000ms")
    private Integer timeoutMs;

    private Boolean autoApproveEnabled;
    private Double autoApproveConfidenceThreshold;
    private String autoApproveFields;
}
