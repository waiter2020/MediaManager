package com.mediamanager.system.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sys_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SysConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "config_key", nullable = false, unique = true, length = 128)
    private String configKey;

    @Column(name = "config_value", columnDefinition = "TEXT")
    private String configValue;

    @Column(length = 256)
    private String description;
}
