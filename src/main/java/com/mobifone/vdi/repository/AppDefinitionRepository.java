package com.mobifone.vdi.repository;

import com.mobifone.vdi.entity.AppDefinition;
import com.mobifone.vdi.entity.enumeration.ActionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppDefinitionRepository extends JpaRepository<AppDefinition, String> {
    boolean existsByCode(String code);

    Page<AppDefinition> findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(
            String codeKw, String nameKw, Pageable pageable);

    Page<AppDefinition> findByActionType(ActionType actionType, Pageable pageable);

    Page<AppDefinition> findByEnabled(Boolean enabled, Pageable pageable);

    Page<AppDefinition> findByActionTypeAndEnabled(ActionType actionType, Boolean enabled, Pageable pageable);
}

