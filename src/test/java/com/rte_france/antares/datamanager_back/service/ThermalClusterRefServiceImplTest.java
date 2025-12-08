package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.repository.ThermalClusterRefRepository;
import com.rte_france.antares.datamanager_back.repository.ThermalTechnologyRepository;
import com.rte_france.antares.datamanager_back.repository.model.ThermalClusterRef;
import com.rte_france.antares.datamanager_back.repository.model.ThermalTechnology;
import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalClusterRefServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.atLeast;

@ExtendWith(MockitoExtension.class)
 class ThermalClusterRefServiceImplTest {

    @Mock
    private ThermalTechnologyRepository  thermalTechnologyRepository;

    @Mock
    private ThermalClusterRefRepository thermalClusterRefRepository;

    @InjectMocks
    private ThermalClusterRefServiceImpl thermalClusterRef;



    @Test
    void findOrCreateThermalClusterRef_shouldCreateAndSaveNewClusterRef() {
        ThermalTechnology technology = ThermalTechnology.builder().name("CCGT").build();
        when(thermalTechnologyRepository.findThermalTechnologyByName("CCGT"))
                .thenReturn(Optional.of(technology));
        when(thermalClusterRefRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ThermalClusterRef result = thermalClusterRef.findOrCreateThermalClusterRef("CCGT", "Cluster2");

        assertNotNull(result);
        assertEquals("Cluster2", result.getName());
        assertEquals("CCGT", result.getThermalTechnology().getName());
        verify(thermalClusterRefRepository, times(1)).save(any());
    }

    @Test
    void findOrCreateThermalClusterRef_whenTechnologyNotFound_shouldThrowBusinessException() {
        // Given
        String technology = "NewTech";
        String name = "ClusterA";
        when(thermalTechnologyRepository.findThermalTechnologyByName(technology)).thenReturn(Optional.empty());

        // Then
        var ex = assertThrows(com.rte_france.antares.datamanager_back.exception.BusinessException.class,
                () -> thermalClusterRef.findOrCreateThermalClusterRef(technology, name));
        assertTrue(ex.getMessage().contains("Technology"));
        verify(thermalTechnologyRepository, never()).save(any());
        verify(thermalClusterRefRepository, never()).save(any());
    }

    @Test
    void findOrCreateThermalClusterRef_whenExistingAndPemmdbIsNA_updatesAndSaves() {
        // Given existing thermalClusterRef in cache with NA PEMMDB
        ThermalTechnology tech = ThermalTechnology.builder().name("oil").build();
        ThermalClusterRef existing = ThermalClusterRef.builder()
                .name("ClusterOil")
                .thermalTechnology(tech)
                .namePemmdb("NA")
                .build();
        when(thermalClusterRefRepository.findAll()).thenReturn(List.of(existing));
        when(thermalClusterRefRepository.save(any(ThermalClusterRef.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When
        ThermalClusterRef result = thermalClusterRef.findOrCreateThermalClusterRef("oil", "ClusterOil", "Oil-123");

        // Then
        assertSame(existing, result);
        assertEquals("Oil-123", result.getNamePemmdb());
        verify(thermalClusterRefRepository, times(1)).save(any(ThermalClusterRef.class));
        verifyNoInteractions(thermalTechnologyRepository);
    }

    @Test
    void findOrCreateThermalClusterRef_whenExistingAndPemmdbAlreadySet_doesNotOverwriteOrSave() {
        // Given existing thermalClusterRef with non-NA PEMMDB
        ThermalTechnology tech = ThermalTechnology.builder().name("CCGT").build();
        ThermalClusterRef existing = ThermalClusterRef.builder()
                .name("ClusterY")
                .thermalTechnology(tech)
                .namePemmdb("EXISTING-VAL")
                .build();
        when(thermalClusterRefRepository.findAll()).thenReturn(List.of(existing));

        // When
        ThermalClusterRef result = thermalClusterRef.findOrCreateThermalClusterRef("CCGT", "ClusterY", "NEW-VAL");

        // Then
        assertSame(existing, result);
        assertEquals("EXISTING-VAL", result.getNamePemmdb());
        verify(thermalClusterRefRepository, never()).save(any());
    }

    @Test
    void findOrCreateThermalClusterRef_whenCreating_setsProvidedPemmdbOrDefaultNA() {
        // First call: empty cache, no existing entries
        when(thermalClusterRefRepository.findAll()).thenReturn(List.of());
        ThermalTechnology tech = ThermalTechnology.builder().name("CCGT").build();
        when(thermalTechnologyRepository.findThermalTechnologyByName("CCGT"))
                .thenReturn(Optional.of(tech));
        when(thermalClusterRefRepository.save(any(ThermalClusterRef.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // With provided pemmdb
        ThermalClusterRef createdWithPemmdb = thermalClusterRef.findOrCreateThermalClusterRef("CCGT", "C1", "PEM1");
        assertEquals("C1", createdWithPemmdb.getName());
        assertEquals("PEM1", createdWithPemmdb.getNamePemmdb());
        assertEquals("CCGT", createdWithPemmdb.getThermalTechnology().getName());

        // With null pemmdb should default to NA (use a different name so it creates a new one)
        ThermalClusterRef createdWithDefault = thermalClusterRef.findOrCreateThermalClusterRef("CCGT", "C2", null);
        assertEquals("C2", createdWithDefault.getName());
        assertEquals("NA", createdWithDefault.getNamePemmdb());

        verify(thermalClusterRefRepository, atLeast(2)).save(any(ThermalClusterRef.class));
    }

}
