package com.mediamanager.system.service;

import com.mediamanager.system.dto.DirectoryDTO;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class DirectoryBrowserService {

    public List<DirectoryDTO> listDirectories(String path) {
        if (path == null || path.trim().isEmpty()) {
            return listRoots();
        }
        return listChildren(new File(path));
    }

    private List<DirectoryDTO> listRoots() {
        List<DirectoryDTO> directories = new ArrayList<>();
        File[] roots = File.listRoots();
        if (roots == null) {
            return directories;
        }
        for (File root : roots) {
            directories.add(DirectoryDTO.builder()
                    .name(root.getAbsolutePath())
                    .path(root.getAbsolutePath())
                    .hasChildren(hasSubDirectories(root))
                    .build());
        }
        return directories;
    }

    private List<DirectoryDTO> listChildren(File dir) {
        List<DirectoryDTO> directories = new ArrayList<>();
        if (!dir.exists() || !dir.isDirectory()) {
            return directories;
        }

        File[] files = dir.listFiles(File::isDirectory);
        if (files == null) {
            return directories;
        }

        Arrays.sort(files, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
        for (File file : files) {
            if (!file.isHidden()) {
                directories.add(DirectoryDTO.builder()
                        .name(file.getName())
                        .path(file.getAbsolutePath())
                        .hasChildren(hasSubDirectories(file))
                        .build());
            }
        }
        return directories;
    }

    private boolean hasSubDirectories(File dir) {
        try {
            File[] files = dir.listFiles(File::isDirectory);
            return files != null && files.length > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
