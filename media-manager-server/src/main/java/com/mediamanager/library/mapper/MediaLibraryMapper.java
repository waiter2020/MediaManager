package com.mediamanager.library.mapper;

import com.mediamanager.library.dto.MediaLibraryResponse;
import com.mediamanager.library.entity.LibraryExtractorConfig;
import com.mediamanager.library.entity.LibraryPath;
import com.mediamanager.library.entity.MediaLibrary;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MediaLibraryMapper {

    @Mapping(target = "extractors", source = "extractorConfigs")
    MediaLibraryResponse toResponse(MediaLibrary library);

    MediaLibraryResponse.PathRes toPathRes(LibraryPath path);

    @Mapping(target = "type", source = "extractorType")
    MediaLibraryResponse.ExtractorRes toExtractorRes(LibraryExtractorConfig config);
}
