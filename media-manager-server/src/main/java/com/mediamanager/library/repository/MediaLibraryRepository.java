package com.mediamanager.library.repository;

import com.mediamanager.library.entity.MediaLibrary;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MediaLibraryRepository extends JpaRepository<MediaLibrary, Integer> {

    @Override
    @EntityGraph(attributePaths = {"paths"})
    List<MediaLibrary> findAll();

    @EntityGraph(attributePaths = {"paths", "extractorConfigs"})
    Optional<MediaLibrary> findWithDetailsById(Integer id);
}
