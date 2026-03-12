package com.mediamanager.system.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sys_permission")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SysPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "group_name", length = 32)
    private String groupName;
}
