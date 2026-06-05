package com.mediamanager.classification.repository;

import com.mediamanager.classification.entity.ClassificationRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClassificationRuleRepository extends JpaRepository<ClassificationRule, Integer> {
    List<ClassificationRule> findByEnabledTrueOrderByPriorityAsc();
}
