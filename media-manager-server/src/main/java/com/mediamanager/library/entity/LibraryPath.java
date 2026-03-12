package com.mediamanager.library.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "library_path")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LibraryPath {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "library_id", nullable = false)
    private MediaLibrary library;

    @Column(nullable = false, length = 1024)
    private String path;

    @Builder.Default
    private Integer priority = 0;
}
