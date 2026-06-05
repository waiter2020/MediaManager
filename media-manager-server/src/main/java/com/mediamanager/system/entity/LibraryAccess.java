package com.mediamanager.system.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "library_access")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LibraryAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private SysUser user;

    @Column(name = "library_id", nullable = false)
    private Integer libraryId;

    @Builder.Default
    @Column(name = "can_view", nullable = false)
    private Boolean canView = true;

    @Builder.Default
    @Column(name = "can_edit", nullable = false)
    private Boolean canEdit = false;

    @Builder.Default
    @Column(name = "can_delete_file", nullable = false)
    private Boolean canDeleteFile = false;
}
