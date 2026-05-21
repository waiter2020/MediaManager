package com.mediamanager.system.service;

import com.mediamanager.common.exception.BusinessException;
import com.mediamanager.common.exception.ErrorCode;
import com.mediamanager.common.security.SecurityCurrentUser;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.system.entity.LibraryAccess;
import com.mediamanager.system.entity.SysRole;
import com.mediamanager.system.entity.SysUser;
import com.mediamanager.system.repository.LibraryAccessRepository;
import com.mediamanager.library.repository.MediaLibraryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LibraryAccessService {

    private final LibraryAccessRepository libraryAccessRepository;
    private final MediaLibraryRepository mediaLibraryRepository;
    private final SecurityCurrentUser securityCurrentUser;

    @Value("${mediamanager.auth.enabled:true}")
    private boolean authEnabled;

    public boolean bypassesLibraryRestrictions(SysUser user) {
        if (user == null) {
            return !authEnabled;
        }
        return user.getRoles().stream()
                .map(SysRole::getCode)
                .anyMatch(code -> "SUPER_ADMIN".equals(code) || "ADMIN".equals(code));
    }

    public Set<Integer> getViewableLibraryIds(Optional<SysUser> user) {
        if (!authEnabled || user.isEmpty()) {
            return mediaLibraryRepository.findAll().stream()
                    .map(lib -> lib.getId())
                    .collect(Collectors.toSet());
        }
        SysUser u = user.get();
        if (bypassesLibraryRestrictions(u)) {
            return mediaLibraryRepository.findAll().stream()
                    .map(lib -> lib.getId())
                    .collect(Collectors.toSet());
        }
        Set<Integer> ids = libraryAccessRepository.findByUser_Id(u.getId()).stream()
                .filter(la -> Boolean.TRUE.equals(la.getCanView()))
                .map(LibraryAccess::getLibraryId)
                .collect(Collectors.toSet());
        return ids.isEmpty() ? Collections.emptySet() : ids;
    }

    public void assertCanViewLibrary(Integer libraryId) {
        Optional<SysUser> user = securityCurrentUser.getCurrentUser();
        Set<Integer> allowed = getViewableLibraryIds(user);
        if (!allowed.contains(libraryId)) {
            throw new BusinessException(ErrorCode.LIBRARY_ACCESS_DENIED);
        }
    }

    public void assertCanViewItem(MediaItem item) {
        if (item == null || item.getLibrary() == null) {
            throw new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND);
        }
        assertCanViewLibrary(item.getLibrary().getId());
    }

    public void assertCanViewFile(MediaFile file) {
        if (file == null || file.getMediaItem() == null || file.getMediaItem().getLibrary() == null) {
            throw new BusinessException(ErrorCode.MEDIA_FILE_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(file.getDeleted())) {
            throw new BusinessException(ErrorCode.MEDIA_FILE_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(file.getMediaItem().getHidden())) {
            throw new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND);
        }
        assertCanViewLibrary(file.getMediaItem().getLibrary().getId());
    }

    public Set<Integer> resolveLibraryFilter(Integer requestedLibraryId) {
        Optional<SysUser> user = securityCurrentUser.getCurrentUser();
        Set<Integer> allowed = getViewableLibraryIds(user);
        if (requestedLibraryId != null) {
            if (!allowed.contains(requestedLibraryId)) {
                throw new BusinessException(ErrorCode.LIBRARY_ACCESS_DENIED);
            }
            return Set.of(requestedLibraryId);
        }
        return allowed;
    }
}
