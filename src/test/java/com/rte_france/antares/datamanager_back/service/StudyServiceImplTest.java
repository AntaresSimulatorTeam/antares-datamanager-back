package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.repository.StudyRepository;
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
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudyServiceImplTest {

    @Mock
    private StudyRepository studyRepository;

    @InjectMocks
    private StudyServiceImpl studyServiceImpl;


    @Test
    void findStudiesByCriteria_returnsFilteredStudiesWhenSearchIsNotNull() {
        Pageable pageable = PageRequest.of(0, 10);
        List<StudyEntity> studies = List.of(new StudyEntity());
        Page<StudyEntity> studyPage = new PageImpl<>(studies);
        String search = "test";

        when(studyRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(studyPage);

        Page<StudyEntity> result = studyServiceImpl.findStudiesByCriteria(search, null, pageable);

        assertEquals(studyPage, result);
        verify(studyRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findStudiesByCriteria_returnsFilteredStudiesWhenIdProjectIsNotNull() {
        Pageable pageable = PageRequest.of(0, 10);
        List<StudyEntity> studies = List.of(new StudyEntity());
        Page<StudyEntity> studyPage = new PageImpl<>(studies);
        Integer idProject = 1;

        when(studyRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(studyPage);

        Page<StudyEntity> result = studyServiceImpl.findStudiesByCriteria(null, idProject, pageable);

        assertEquals(studyPage, result);
        verify(studyRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findStudiesByCriteria_returnsFilteredStudiesWhenSearchAndIdProjectAreNotNull() {
        Pageable pageable = PageRequest.of(0, 10);
        List<StudyEntity> studies = List.of(new StudyEntity());
        Page<StudyEntity> studyPage = new PageImpl<>(studies);
        String search = "test";
        Integer idProject = 1;

        when(studyRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(studyPage);

        Page<StudyEntity> result = studyServiceImpl.findStudiesByCriteria(search, idProject, pageable);

        assertEquals(studyPage, result);
        verify(studyRepository).findAll(any(Specification.class), eq(pageable));
    }
}
