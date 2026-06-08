package com.backend.mapper;

import com.backend.dto.packcategory.CreatePackCategoryDTO;
import com.backend.dto.packcategory.PackCategoryResponseDTO;
import com.backend.dto.packcategory.UpdatePackCategoryDTO;
import com.backend.entity.PackCategory;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface PackCategoryMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "packs", ignore = true)
    PackCategory toEntity(CreatePackCategoryDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "packs", ignore = true)
    void updateEntity(UpdatePackCategoryDTO dto, @MappingTarget PackCategory entity);

    @Mapping(target = "packCount", expression = "java(entity.getPacks() != null ? entity.getPacks().size() : 0)")
    PackCategoryResponseDTO toResponseDTO(PackCategory entity);
}

