package com.mobifone.vdi.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.mobifone.vdi.entity.Permission;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, String> {
    Page<Permission> findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
            String nameLike, String descLike, Pageable pageable
    );
}
