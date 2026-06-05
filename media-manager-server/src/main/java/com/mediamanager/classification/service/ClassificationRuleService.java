package com.mediamanager.classification.service;

import com.mediamanager.classification.dto.ClassificationRuleRequest;
import com.mediamanager.classification.dto.ClassificationRuleResponse;
import com.mediamanager.classification.entity.ClassificationRule;
import com.mediamanager.classification.repository.ClassificationRuleRepository;
import com.mediamanager.common.exception.BusinessException;
import com.mediamanager.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClassificationRuleService {

    private final ClassificationRuleRepository ruleRepository;

    public List<ClassificationRuleResponse> getAllRules() {
        return ruleRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ClassificationRuleResponse createRule(ClassificationRuleRequest request) {
        ClassificationRule rule = ClassificationRule.builder()
                .name(request.getName())
                .ruleType(request.getRuleType())
                .expression(request.getExpression())
                .targetType(request.getTargetType())
                .targetValue(request.getTargetValue())
                .enabled(request.getEnabled())
                .priority(request.getPriority())
                .build();
        return toResponse(ruleRepository.save(rule));
    }

    @Transactional
    public ClassificationRuleResponse updateRule(Integer id, ClassificationRuleRequest request) {
        ClassificationRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        if (request.getName() != null) rule.setName(request.getName());
        if (request.getRuleType() != null) rule.setRuleType(request.getRuleType());
        if (request.getExpression() != null) rule.setExpression(request.getExpression());
        if (request.getTargetType() != null) rule.setTargetType(request.getTargetType());
        if (request.getTargetValue() != null) rule.setTargetValue(request.getTargetValue());
        if (request.getEnabled() != null) rule.setEnabled(request.getEnabled());
        if (request.getPriority() != null) rule.setPriority(request.getPriority());

        return toResponse(ruleRepository.save(rule));
    }

    @Transactional
    public void deleteRule(Integer id) {
        ClassificationRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        ruleRepository.delete(rule);
    }

    private ClassificationRuleResponse toResponse(ClassificationRule rule) {
        return ClassificationRuleResponse.builder()
                .id(rule.getId())
                .name(rule.getName())
                .ruleType(rule.getRuleType())
                .expression(rule.getExpression())
                .targetType(rule.getTargetType())
                .targetValue(rule.getTargetValue())
                .enabled(rule.getEnabled())
                .priority(rule.getPriority())
                .createdAt(rule.getCreatedAt())
                .build();
    }
}
