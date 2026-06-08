package com.mediamanager.integration;

import com.mediamanager.common.security.JwtTokenProvider;
import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.library.repository.MediaLibraryRepository;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaFileRepository;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.system.entity.LibraryAccess;
import com.mediamanager.system.entity.SysRole;
import com.mediamanager.system.entity.SysUser;
import com.mediamanager.system.repository.LibraryAccessRepository;
import com.mediamanager.system.repository.SysRoleRepository;
import com.mediamanager.system.repository.SysUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

public abstract class IntegrationTestSupport extends PostgresIntegrationTestBase {

    @Autowired
    protected SysUserRepository userRepository;
    @Autowired
    protected SysRoleRepository roleRepository;
    @Autowired
    protected LibraryAccessRepository libraryAccessRepository;
    @Autowired
    protected MediaLibraryRepository libraryRepository;
    @Autowired
    protected MediaItemRepository mediaItemRepository;
    @Autowired
    protected MediaFileRepository mediaFileRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected JwtTokenProvider tokenProvider;

    protected String bearerToken(SysUser user, Set<String> permissions) {
        return "Bearer " + tokenProvider.generateAccessToken(user.getId(), user.getUsername(), permissions);
    }

    protected SysUser createUser(String username, String roleCode) {
        SysRole role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalStateException("Role missing: " + roleCode));
        SysUser user = SysUser.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode("password"))
                .displayName(username)
                .enabled(true)
                .build();
        user.getRoles().add(role);
        return userRepository.save(user);
    }

    protected MediaLibrary createLibrary(String name) {
        MediaLibrary lib = MediaLibrary.builder()
                .name(name)
                .type("MOVIE")
                .language("zh")
                .autoScan(false)
                .scanIntervalMinutes(30)
                .build();
        return libraryRepository.save(lib);
    }

    protected MediaItem createItem(MediaLibrary library, String title) {
        MediaItem item = MediaItem.builder()
                .library(library)
                .title(title)
                .type("MOVIE")
                .status("UNIDENTIFIED")
                .hidden(false)
                .build();
        return mediaItemRepository.save(item);
    }

    protected MediaFile createFile(MediaItem item, String path) {
        MediaFile file = MediaFile.builder()
                .mediaItem(item)
                .filePath(path)
                .fileName("test.mkv")
                .fileSize(1024L)
                .deleted(false)
                .build();
        return mediaFileRepository.save(file);
    }

    protected void grantLibraryView(SysUser user, Integer libraryId) {
        LibraryAccess access = LibraryAccess.builder()
                .user(user)
                .libraryId(libraryId)
                .canView(true)
                .canEdit(false)
                .canDeleteFile(false)
                .build();
        libraryAccessRepository.save(access);
    }
}
