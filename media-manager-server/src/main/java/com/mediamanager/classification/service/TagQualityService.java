package com.mediamanager.classification.service;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class TagQualityService {

    private static final int MAX_AI_TAG_CHARS = 32;
    private static final int MAX_AI_TAG_WORDS = 4;
    private static final Pattern NOISY_PUNCTUATION = Pattern.compile("[{}\\[\\]<>`]|=>|\\r|\\n|,|:|;");
    private static final Pattern SENTENCE_ENDING = Pattern.compile("[.!?。！？]$");
    private static final Set<String> PROMPT_ARTIFACTS = Set.of(
            "i'm sorry",
            "i am sorry",
            "sorry",
            "here are",
            "happy to assist",
            "appropriate",
            "descriptive",
            "compliant",
            "comma-separated",
            "avoid explicit",
            "prohibited terms",
            "preserving context",
            "classification",
            "return only",
            "raw json",
            "input json",
            "as requested",
            "cannot provide",
            "can't assist");

    public Optional<String> cleanupReason(
            String rawName,
            long usageCount,
            String source,
            int lowUsageThreshold,
            boolean protectManualTags) {
        Optional<String> qualityIssue = qualityIssue(rawName);
        if (qualityIssue.isPresent()) {
            return qualityIssue;
        }
        if (lowUsageThreshold >= 0
                && usageCount <= lowUsageThreshold
                && (!protectManualTags || !isManual(source))) {
            return Optional.of("引用数 " + usageCount + "，低于通用标签阈值");
        }
        return Optional.empty();
    }

    public boolean isAcceptableAiTag(String rawName) {
        return qualityIssue(rawName).isEmpty();
    }

    public Optional<String> qualityIssue(String rawName) {
        String name = normalize(rawName);
        if (name.isBlank()) {
            return Optional.of("空标签");
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String artifact : PROMPT_ARTIFACTS) {
            if (lower.contains(artifact)) {
                return Optional.of("疑似 AI 回复残片");
            }
        }
        if (NOISY_PUNCTUATION.matcher(name).find()) {
            return Optional.of("包含提示词或句子标点");
        }
        if (SENTENCE_ENDING.matcher(name).find()) {
            return Optional.of("句子化标签");
        }
        if (name.codePointCount(0, name.length()) > MAX_AI_TAG_CHARS) {
            return Optional.of("标签过长，不适合作为通用标签");
        }
        if (wordCount(name) > MAX_AI_TAG_WORDS) {
            return Optional.of("词组过长，不适合作为通用标签");
        }
        if (lower.startsWith("i ") || lower.startsWith("i'") || lower.startsWith("the ")) {
            return Optional.of("疑似自然语言片段");
        }
        return Optional.empty();
    }

    private boolean isManual(String source) {
        return "MANUAL".equalsIgnoreCase(source);
    }

    private int wordCount(String value) {
        String latin = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ")
                .trim();
        if (latin.isBlank()) {
            return 0;
        }
        return latin.split("\\s+").length;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC).replaceAll("\\s+", " ").strip();
    }
}
