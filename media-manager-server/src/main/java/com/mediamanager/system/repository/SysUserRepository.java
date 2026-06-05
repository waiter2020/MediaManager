package com.mediamanager.system.repository;

import com.mediamanager.system.entity.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Integer> {
    Optional<SysUser> findByUsername(String username);
    boolean existsByUsername(String username);
    long count();
}
