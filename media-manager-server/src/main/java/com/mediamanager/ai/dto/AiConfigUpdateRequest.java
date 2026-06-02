package com.mediamanager.ai.dto;

import lombok.Data;
import jakarta.validation.constraints.*;

@Data
public class AiConfigUpdateRequest {
    @NotBlank(message = "Default provider is required")
    @Size(max = 64, message = "Provider ID cannot exceed 64 characters")
    private String defaultProvider;

    @Size(max = 255, message = "Ollama URL cannot exceed 255 characters")
    @Pattern(regexp = "^(https?://.*)?$", message = "Ollama base URL must start with http:// or https://")
    private String ollamaBaseUrl;

    @Size(max = 255, message = "OpenAI URL cannot exceed 255 characters")
    @Pattern(regexp = "^(https?://.*)?$", message = "OpenAI base URL must start with http:// or https://")
    private String openaiBaseUrl;

    @Size(max = 255, message = "OpenAI API Key cannot exceed 255 characters")
    private String openaiApiKey;

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
