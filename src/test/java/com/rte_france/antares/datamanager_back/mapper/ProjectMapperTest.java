package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.dto.ProjectDto;
import com.rte_france.antares.datamanager_back.repository.model.ProjectEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectMapperTest {

    @Test
    void toProjectDto_returnsCorrectDto() {
        ProjectEntity projectEntity = new ProjectEntity();
        projectEntity.setId(1);
        projectEntity.setName("testProject");
        projectEntity.setCreationDate(Instant.now());
        projectEntity.setCreatedBy("testUser");
        projectEntity.setStudies(List.of(new StudyEntity()));

        ProjectDto projectDto = ProjectMapper.toProjectDto(projectEntity);

        assertEquals(projectEntity.getId(), projectDto.getId());
        assertEquals(projectEntity.getName(), projectDto.getName());
        assertEquals(projectEntity.getCreationDate(), projectDto.getCreationDate());
        assertEquals(projectEntity.getCreatedBy(), projectDto.getCreatedBy());
        assertEquals(projectEntity.getStudies().stream().map(StudyEntity::getId).toList(), projectDto.getStudies());
    }
}