package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.dto.ProjectDto;
import com.rte_france.antares.datamanager_back.repository.model.ProjectEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectMapperTest {

    @Test
    void toProjectDto_returnsCorrectDto() {
        ProjectEntity projectEntity = new ProjectEntity();
        projectEntity.setId(1);
        projectEntity.setName("testProject");
        projectEntity.setCreationDate(LocalDateTime.now());
        projectEntity.setCreatedBy("testUser");
        projectEntity.setStudies(List.of(new StudyEntity()));

        ProjectDto projectDto = ProjectMapper.toProjectDto(projectEntity);

        assertEquals(projectEntity.getId(), projectDto.getId());
        assertEquals(projectEntity.getName(), projectDto.getName());
        assertEquals(projectEntity.getCreationDate(), projectDto.getCreationDate());
        assertEquals(projectEntity.getCreatedBy(), projectDto.getCreatedBy());
        assertEquals(projectEntity.getStudies().stream().map(StudyEntity::getId).toList(), projectDto.getStudies());
    }

        @Test
        void toProjectDtos_returnsCorrectDtos() {
            ProjectEntity projectEntity1 = new ProjectEntity();
            projectEntity1.setId(1);
            projectEntity1.setName("testProject1");
            projectEntity1.setCreationDate(LocalDateTime.now());
            projectEntity1.setCreatedBy("testUser1");

            ProjectEntity projectEntity2 = new ProjectEntity();
            projectEntity2.setId(2);
            projectEntity2.setName("testProject2");
            projectEntity2.setCreationDate(LocalDateTime.now());
            projectEntity2.setCreatedBy("testUser2");

            List<ProjectDto> projectDtos = ProjectMapper.toProjectDtos(List.of(projectEntity1, projectEntity2));

            assertEquals(2, projectDtos.size());
            assertEquals(projectEntity1.getId(), projectDtos.get(0).getId());
            assertEquals(projectEntity1.getName(), projectDtos.get(0).getName());
            assertEquals(projectEntity1.getCreationDate(), projectDtos.get(0).getCreationDate());
            assertEquals(projectEntity1.getCreatedBy(), projectDtos.get(0).getCreatedBy());

            assertEquals(projectEntity2.getId(), projectDtos.get(1).getId());
            assertEquals(projectEntity2.getName(), projectDtos.get(1).getName());
            assertEquals(projectEntity2.getCreationDate(), projectDtos.get(1).getCreationDate());
            assertEquals(projectEntity2.getCreatedBy(), projectDtos.get(1).getCreatedBy());
        }

        @Test
        void toProjectDtos_returnsEmptyListWhenInputIsEmpty() {
            List<ProjectDto> projectDtos = ProjectMapper.toProjectDtos(List.of());

            assertEquals(0, projectDtos.size());
        }

        @Test
        void toProjectDtos_handlesNullStudies() {
            ProjectEntity projectEntity = new ProjectEntity();
            projectEntity.setId(1);
            projectEntity.setName("testProject");
            projectEntity.setCreationDate(LocalDateTime.now());
            projectEntity.setCreatedBy("testUser");
            projectEntity.setStudies(null);

            List<ProjectDto> projectDtos = ProjectMapper.toProjectDtos(List.of(projectEntity));

            assertEquals(1, projectDtos.size());
            assertEquals(projectEntity.getId(), projectDtos.get(0).getId());
            assertEquals(projectEntity.getName(), projectDtos.get(0).getName());
            assertEquals(projectEntity.getCreationDate(), projectDtos.get(0).getCreationDate());
            assertEquals(projectEntity.getCreatedBy(), projectDtos.get(0).getCreatedBy());
            assertEquals(List.of(), projectDtos.get(0).getStudies());
        }
    }
