package com.mediamanager.system.repository;

import com.mediamanager.system.entity.LibraryAccess;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LibraryAccessRepository extends JpaRepository<LibraryAccess, Integer> {
    List<LibraryAccess> findByUser_Id(Integer userId);
    void deleteByUser_Id(Integer userId);
}
