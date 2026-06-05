package com.mediamanager.system.repository;

import com.mediamanager.system.entity.SysConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SysConfigRepository extends JpaRepository<SysConfig, Integer> {
    Optional<SysConfig> findByConfigKey(String configKey);
}
