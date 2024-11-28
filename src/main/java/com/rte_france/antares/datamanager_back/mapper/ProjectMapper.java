package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.dto.ProjectDto;
import com.rte_france.antares.datamanager_back.repository.model.ProjectEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;
import org.springframework.data.domain.Page;

import java.util.List;

@Value
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ProjectMapper {

    public static ProjectDto toProjectDto(ProjectEntity projectEntity) {
        return ProjectDto.builder()
                .id(projectEntity.getId())
                .name(projectEntity.getName())
                .creationDate(projectEntity.getCreationDate())
                .createdBy(projectEntity.getCreatedBy())
                .studies(projectEntity.getStudies() == null ? List.of() : projectEntity.getStudies().stream().map(StudyEntity::getId).toList())
                .tags(projectEntity.getTags())
                .description(projectEntity.getDescription())
                .build();
    }

    public static List<ProjectDto> toProjectDtos(List<ProjectEntity> projectEntities) {
        return projectEntities.stream()
                .map(ProjectMapper::toProjectDto)
                .toList();
    }

    public static Page<ProjectDto> toProjectPage(Page<ProjectEntity> page) {
        return page.map(ProjectMapper::toProjectDto);
    }

}