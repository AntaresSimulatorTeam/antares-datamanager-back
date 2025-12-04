package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.DefaultLoadDTO;
import com.rte_france.antares.datamanager_back.dto.DefaultThermalTechnologyDTO;

import com.rte_france.antares.datamanager_back.repository.DefaultLoadRepository;
import com.rte_france.antares.datamanager_back.repository.ThermalTechnologyRepository;
import com.rte_france.antares.datamanager_back.repository.model.DefaultLoadEntity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalTechnology;
import com.rte_france.antares.datamanager_back.service.common.impl.DefaultConfigServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
 class DefaultConfigServiceImplTest {
    @Mock
    private DefaultLoadRepository defaultLoadRepository;

    @Mock
    private ThermalTechnologyRepository thermalTechnologyRepository;

    @InjectMocks
    private DefaultConfigServiceImpl loadDefaultService;


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


    @Test
    void fetchAllDefaults_shouldReturnMappedDTOsSortedByName() {
        // Given
        DefaultLoadEntity entity1 = new DefaultLoadEntity();
        entity1.setName("RO");
        DefaultLoadEntity entity2 = new DefaultLoadEntity();
        entity2.setName("ES");
        DefaultLoadEntity entity3 = new DefaultLoadEntity();
        entity3.setName("AR");

        List<DefaultLoadEntity> entities = List.of(entity1, entity2, entity3);


        when(defaultLoadRepository.findAllByIsDefaultIsTrue()).thenReturn(entities);

        // When
        List<DefaultLoadDTO> result = loadDefaultService.fetchAllDefaults();

        // Then
        assertEquals("AR", result.get(0).getName());
        assertEquals("ES", result.get(1).getName());
        assertEquals("RO", result.get(2).getName());
        verify(defaultLoadRepository).findAllByIsDefaultIsTrue();
    }

    @Test
    void fetchAllThermalTechnologies_shouldReturnMappedDTOs() {
        // Given
        ThermalTechnology t1 = ThermalTechnology.builder().id(1).name("Nuclear").build();
        ThermalTechnology t2 = ThermalTechnology.builder().id(2).name("CCGT").build();
        when(thermalTechnologyRepository.findAll()).thenReturn(List.of(t1, t2));

        // When
        List<DefaultThermalTechnologyDTO> result = loadDefaultService.fetchAllThermalTechnologies();

        // Then
        // Mapper keeps only name; ensure both mapped
        assertEquals(2, result.size());
        verify(thermalTechnologyRepository).findAll();
    }

    @Test
    void fetchAllThermalTechnologies_shouldReturnMappedDTOsSortedByName() {
        // Given: unordered names
        ThermalTechnology t1 = ThermalTechnology.builder().id(1).name("Nuclear").build();
        ThermalTechnology t2 = ThermalTechnology.builder().id(2).name("CCGT").build();
        ThermalTechnology t3 = ThermalTechnology.builder().id(3).name("Additional power").build();
        when(thermalTechnologyRepository.findAll()).thenReturn(List.of(t1, t2, t3));

        // When
        List<DefaultThermalTechnologyDTO> result = loadDefaultService.fetchAllThermalTechnologies();

        // Then: ascending alphabetical order by name
        assertEquals("Additional power", result.get(0).getName());
        assertEquals("CCGT", result.get(1).getName());
        assertEquals("Nuclear", result.get(2).getName());
        verify(thermalTechnologyRepository).findAll();
    }
}
