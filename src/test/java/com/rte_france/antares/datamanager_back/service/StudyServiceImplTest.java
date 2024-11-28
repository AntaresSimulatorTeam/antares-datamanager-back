package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.model.ProjectEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.service.impl.StudyServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import static org.assertj.core.api.Assertions.assertThat;


import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudyServiceImplTest {

    @Mock
    private StudyRepository studyRepository;

    @InjectMocks
    private StudyServiceImpl studyService;

    @Test
    void findStudiesByCriteria_returnsAllStudiesWhenSearchIsNull() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<StudyEntity> expectedPage = new PageImpl<>(List.of(StudyEntity.builder().name("study1").build()), pageable, 1);

        when(studyRepository.findAll(pageable)).thenReturn(expectedPage);

        Page<StudyEntity> result = studyService.findStudiesByCriteria(null, pageable);

        assertEquals(expectedPage, result);
        verify(studyRepository, times(1)).findAll(pageable);
    }

    @Test
    void findStudyEntitiesByProjectId_returnsListOfStudyEntities() {
        Integer projectId = 1;
        List<StudyEntity> expectedStudies = List.of(
                StudyEntity.builder().project(ProjectEntity.builder().id(projectId).build()).name("Study1").build(),
                StudyEntity.builder().project(ProjectEntity.builder().id(projectId).build()).name("Study2").build()
        );

        when(studyRepository.findStudyEntitiesByProjectId(projectId)).thenReturn(expectedStudies);

        List<StudyEntity> studies = studyService.getStudiesByProjectId(projectId);

        assertThat(studies).isNotNull().isNotEmpty();
        assertThat(studies).extracting(StudyEntity::getProject).extracting(ProjectEntity::getId).containsOnly(projectId);
        assertThat(studies).extracting(StudyEntity::getName).containsExactlyInAnyOrder("Study1", "Study2");
    }

    @Test
    void findStudyEntitiesByProjectId_returnsEmptyListForNonExistentProjectId() {
        Integer projectId = -1;

        when(studyRepository.findStudyEntitiesByProjectId(projectId)).thenReturn(List.of());

        List<StudyEntity> studies = studyService.getStudiesByProjectId(projectId);

        assertThat(studies).isNotNull().isEmpty();
    }

}
