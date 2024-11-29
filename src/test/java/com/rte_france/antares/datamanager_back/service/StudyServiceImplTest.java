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
}
