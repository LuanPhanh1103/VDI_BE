package com.mobifone.vdi.mapper;

import com.mobifone.vdi.dto.request.AppDefinitionRequest;
import com.mobifone.vdi.dto.response.AppDefinitionResponse;
import com.mobifone.vdi.entity.AppDefinition;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface AppDefinitionMapper {
    AppDefinition toEntity(AppDefinitionRequest request);

    AppDefinitionResponse toResponse(AppDefinition entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void update(@MappingTarget AppDefinition target, AppDefinitionRequest request);
}
