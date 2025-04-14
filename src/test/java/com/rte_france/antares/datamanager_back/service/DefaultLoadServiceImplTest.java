package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.DefaultLoadDTO;
import com.rte_france.antares.datamanager_back.mapper.DefaultLoadMapper;
import com.rte_france.antares.datamanager_back.mapper.DefaultLoadMapperTest;
import com.rte_france.antares.datamanager_back.repository.DefaultLoadRepository;
import com.rte_france.antares.datamanager_back.repository.model.DefaultLoadEntity;
import com.rte_france.antares.datamanager_back.service.impl.DefaultLoadServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DefaultLoadServiceImplTest {
    @Mock
    private DefaultLoadRepository defaultLoadRepository;

    @InjectMocks
    private DefaultLoadServiceImpl loadDefaultService;


    @Test
    void fetchAllDefaults_shouldReturnMappedDTOs() {
        // Given
        DefaultLoadEntity entity = new DefaultLoadEntity();
        List<DefaultLoadEntity> entities = List.of(entity);

        DefaultLoadDTO dto = new DefaultLoadDTO();
        List<DefaultLoadDTO> expectedResult = List.of(dto);

        when(defaultLoadRepository.findAllByIsDefaultIsTrue()).thenReturn(entities);

        // When
        List<DefaultLoadDTO> result = loadDefaultService.fetchAllDefaults();

        // Then
        assertEquals(expectedResult, result);
        verify(defaultLoadRepository).findAllByIsDefaultIsTrue();

    }
}
