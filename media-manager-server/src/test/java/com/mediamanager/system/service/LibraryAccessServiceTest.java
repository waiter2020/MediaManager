package com.mediamanager.system.service;

import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.library.repository.MediaLibraryRepository;
import com.mediamanager.system.entity.LibraryAccess;
import com.mediamanager.system.entity.SysRole;
import com.mediamanager.system.entity.SysUser;
import com.mediamanager.system.repository.LibraryAccessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LibraryAccessServiceTest {

    @Mock
    private LibraryAccessRepository libraryAccessRepository;
    @Mock
    private MediaLibraryRepository mediaLibraryRepository;
    @Mock
    private com.mediamanager.common.security.SecurityCurrentUser securityCurrentUser;
    @Mock
    private SysConfigService sysConfigService;

    @InjectMocks
    private LibraryAccessService libraryAccessService;

    @BeforeEach
    void enableAuth() {
        when(sysConfigService.isEffectiveAuthEnabled(false)).thenReturn(true);
    }

    @Test
    void adminSeesAllLibraries() {
        SysUser admin = userWithRole("ADMIN");
        MediaLibrary lib = MediaLibrary.builder().id(1).name("A").build();
        when(mediaLibraryRepository.findAll()).thenReturn(List.of(lib));

        Set<Integer> ids = libraryAccessService.getViewableLibraryIds(Optional.of(admin));
        assertEquals(Set.of(1), ids);
    }

    @Test
    void userWithAccessRowSeesOnlyGrantedLibrary() {
        SysUser user = userWithRole("USER");
        user.setId(10);
        LibraryAccess access = LibraryAccess.builder()
                .user(user)
                .libraryId(5)
                .canView(true)
                .build();
        when(libraryAccessRepository.findByUser_Id(10)).thenReturn(List.of(access));

        Set<Integer> ids = libraryAccessService.getViewableLibraryIds(Optional.of(user));
        assertEquals(Set.of(5), ids);
    }

    @Test
    void userWithoutRowsSeesNothing() {
        SysUser user = userWithRole("USER");
        user.setId(11);
        when(libraryAccessRepository.findByUser_Id(11)).thenReturn(List.of());

        Set<Integer> ids = libraryAccessService.getViewableLibraryIds(Optional.of(user));
        assertTrue(ids.isEmpty());
    }

    private SysUser userWithRole(String code) {
        SysRole role = new SysRole();
        role.setCode(code);
        SysUser user = new SysUser();
        HashSet<SysRole> roles = new HashSet<>();
        roles.add(role);
        user.setRoles(roles);
        return user;
    }
}
