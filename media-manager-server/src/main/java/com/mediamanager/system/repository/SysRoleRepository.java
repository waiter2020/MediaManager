package com.mediamanager.system.repository;

import com.mediamanager.system.entity.SysRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SysRoleRepository extends JpaRepository<SysRole, Integer> {
    Optional<SysRole> findByCode(String code);
}
