package com.mediamanager.classification.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "classification_rule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassificationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "rule_type", length = 32, nullable = false)
    private String ruleType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String expression;

    @Column(name = "target_type", length = 32, nullable = false)
    private String targetType;

    @Column(name = "target_value", length = 256, nullable = false)
    private String targetValue;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = true;

    @Builder.Default
    @Column(nullable = false)
    private Integer priority = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
