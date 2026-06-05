package com.mediamanager.plugin.entity;

import com.mediamanager.library.entity.MediaLibrary;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "library_plugin_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LibraryPluginConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "library_id", nullable = false)
    private MediaLibrary library;

    @Column(name = "plugin_id", nullable = false, length = 64)
    private String pluginId;

    @Column(nullable = false, length = 32)
    private String kind;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = true;

    @Builder.Default
    @Column(nullable = false)
    private Integer priority = 100;

    @Column(columnDefinition = "TEXT")
    private String config;
}
