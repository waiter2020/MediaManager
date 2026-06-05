package com.mediamanager.plugin.repository;

import com.mediamanager.plugin.entity.LibraryPluginConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LibraryPluginConfigRepository extends JpaRepository<LibraryPluginConfig, Integer> {
    List<LibraryPluginConfig> findByLibrary_IdAndKindAndEnabledTrueOrderByPriorityAsc(
            Integer libraryId, String kind);

    List<LibraryPluginConfig> findByLibrary_IdOrderByPriorityAsc(Integer libraryId);
}
