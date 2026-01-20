package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThermalClusterRefServiceImplTest {

    @Mock
    private ThermalTechnologyRepository thermalTechnologyRepository;

    @Mock
    private ThermalClusterRefRepository thermalClusterRefRepository;

    @InjectMocks
    private ThermalClusterRefServiceImpl thermalClusterRef;


    @Test
    void findOrCreateThermalClusterRef_trimsTechnologyAndClusterNameBeforeLookupAndCreation() {
        ThermalTechnology tech = ThermalTechnology.builder().name("CCGT").build();
        when(thermalClusterRefRepository.findByThermalTechnology_NameIgnoreCaseAndNameIgnoreCase("CCGT", "C1"))
                .thenReturn(Optional.empty());
        when(thermalTechnologyRepository.findThermalTechnologyByNameIgnoreCase("CCGT"))
                .thenReturn(Optional.of(tech));
        when(thermalClusterRefRepository.save(any(ThermalClusterRef.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ThermalClusterRef result =
                thermalClusterRef.findOrCreateThermalClusterRef("CCGT", "C1", "PEM1");

        assertNotNull(result);
        assertEquals("C1", result.getName());
        assertEquals("PEM1", result.getNamePemmdb());
        assertEquals("CCGT", result.getThermalTechnology().getName());

        verify(thermalTechnologyRepository).findThermalTechnologyByNameIgnoreCase("CCGT");
        verify(thermalClusterRefRepository).save(any(ThermalClusterRef.class));
    }

    @Test
    void findOrCreateThermalClusterRef_whenClusterNameIsNull_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> thermalClusterRef.findOrCreateThermalClusterRef("CCGT", null, "PEM123"));

        verifyNoInteractions(thermalClusterRefRepository);
    }

    @Test
    void findOrCreateThermalClusterRef_withBlankPemmdb_doesNotOverwriteExistingNA() {
        ThermalTechnology tech = ThermalTechnology.builder().name("oil").build();
        ThermalClusterRef existing = ThermalClusterRef.builder()
                .id(1)
                .name("ClusterOil")
                .thermalTechnology(tech)
                .namePemmdb("NA")
                .build();

        when(thermalClusterRefRepository.findByThermalTechnology_NameIgnoreCaseAndNameIgnoreCase("oil", "ClusterOil"))
                .thenReturn(Optional.of(existing));

        ThermalClusterRef result =
                thermalClusterRef.findOrCreateThermalClusterRef("oil", "ClusterOil", "   ");

        assertSame(existing, result);
        assertEquals("NA", result.getNamePemmdb());

        verifyNoInteractions(thermalTechnologyRepository);
        verify(thermalClusterRefRepository, never()).save(any());
    }

    @Test
    void updatePemmdb_overwritesWhenExistingIsNA() {
        ThermalTechnology tech = ThermalTechnology.builder().name("coal").build();
        ThermalClusterRef existing = ThermalClusterRef.builder()
                .id(11)
                .name("ClusterCoal")
                .thermalTechnology(tech)
                .namePemmdb("NA")
                .build();

        when(thermalClusterRefRepository.findByThermalTechnology_NameIgnoreCaseAndNameIgnoreCase("coal", "ClusterCoal"))
                .thenReturn(Optional.of(existing));

        ThermalClusterRef result =
                thermalClusterRef.findOrCreateThermalClusterRef("coal", "ClusterCoal", "PEM-Y");

        assertSame(existing, result);
        assertEquals("PEM-Y", result.getNamePemmdb());

        verifyNoInteractions(thermalTechnologyRepository);
        verify(thermalClusterRefRepository, never()).save(any());
    }

    @Test
    void updatePemmdb_setsWhenExistingIsNull() {
        ThermalTechnology tech = ThermalTechnology.builder().name("gas").build();
        ThermalClusterRef existing = ThermalClusterRef.builder()
                .id(10)
                .name("ClusterGas")
                .thermalTechnology(tech)
                .namePemmdb(null)
                .build();

        when(thermalClusterRefRepository.findByThermalTechnology_NameIgnoreCaseAndNameIgnoreCase("gas", "ClusterGas"))
                .thenReturn(Optional.of(existing));

        ThermalClusterRef result =
                thermalClusterRef.findOrCreateThermalClusterRef("gas", "ClusterGas", "PEM-X");

        assertSame(existing, result);
        assertEquals("PEM-X", result.getNamePemmdb());

        verifyNoInteractions(thermalTechnologyRepository);
        verify(thermalClusterRefRepository, never()).save(any());
    }


    @Test
    void findOrCreateThermalClusterRef_withoutTechnology_createsClusterWithNullTechnology() {
        when(thermalClusterRefRepository.findByThermalTechnologyIsNullAndNameIgnoreCase("ClusterX"))
                .thenReturn(Optional.empty());
        when(thermalClusterRefRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        ThermalClusterRef result =
                thermalClusterRef.findOrCreateThermalClusterRef(null, "ClusterX", "PEM-NULL");

        assertNotNull(result);
        assertEquals("ClusterX", result.getName());
        assertEquals("PEM-NULL", result.getNamePemmdb());
        assertNull(result.getThermalTechnology());

        verify(thermalClusterRefRepository)
                .findByThermalTechnologyIsNullAndNameIgnoreCase("ClusterX");
        verifyNoInteractions(thermalTechnologyRepository);
    }

    @Test
    void findOrCreateThermalClusterRef_withNullPemmdb_setsNA() {
        ThermalTechnology tech = ThermalTechnology.builder().name("nuclear").build();

        when(thermalClusterRefRepository.findByThermalTechnology_NameIgnoreCaseAndNameIgnoreCase("nuclear", "C2"))
                .thenReturn(Optional.empty());
        when(thermalTechnologyRepository.findThermalTechnologyByNameIgnoreCase("nuclear"))
                .thenReturn(Optional.of(tech));
        when(thermalClusterRefRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        ThermalClusterRef result =
                thermalClusterRef.findOrCreateThermalClusterRef("nuclear", "C2", null);

        assertEquals("NA", result.getNamePemmdb());
    }

    @Test
    void findOrCreateThermalClusterRef_whenTechnologyDoesNotExist_throwsBusinessException() {
        when(thermalClusterRefRepository.findByThermalTechnology_NameIgnoreCaseAndNameIgnoreCase("unknown", "C3"))
                .thenReturn(Optional.empty());
        when(thermalTechnologyRepository.findThermalTechnologyByNameIgnoreCase("unknown"))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> thermalClusterRef.findOrCreateThermalClusterRef("unknown", "C3", "PEM-Z")
        );

        assertTrue(ex.getMessage().contains("Technology unknown does not exist"));
        verify(thermalClusterRefRepository, never()).save(any());
    }

    @Test
    void updatePemmdb_doesNotOverwriteWhenExistingIsAlreadySet() {
        ThermalTechnology tech = ThermalTechnology.builder().name("hydro").build();
        ThermalClusterRef existing = ThermalClusterRef.builder()
                .id(20)
                .name("ClusterHydro")
                .thermalTechnology(tech)
                .namePemmdb("PEM-OLD")
                .build();

        when(thermalClusterRefRepository.findByThermalTechnology_NameIgnoreCaseAndNameIgnoreCase("hydro", "ClusterHydro"))
                .thenReturn(Optional.of(existing));

        ThermalClusterRef result =
                thermalClusterRef.findOrCreateThermalClusterRef("hydro", "ClusterHydro", "PEM-NEW");

        assertSame(existing, result);
        assertEquals("PEM-OLD", result.getNamePemmdb());

        verify(thermalClusterRefRepository, never()).save(any());
    }

    @Test
    void findOrCreateThermalClusterRef_trimsClusterNameBeforeLookup() {
        ThermalTechnology tech = ThermalTechnology.builder().name("biomass").build();

        when(thermalClusterRefRepository
                .findByThermalTechnology_NameIgnoreCaseAndNameIgnoreCase("biomass", "C4"))
                .thenReturn(Optional.empty());
        when(thermalTechnologyRepository.findThermalTechnologyByNameIgnoreCase("biomass"))
                .thenReturn(Optional.of(tech));
        when(thermalClusterRefRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        thermalClusterRef.findOrCreateThermalClusterRef("biomass", "  C4  ", "PEM-BIO");

        verify(thermalClusterRefRepository)
                .findByThermalTechnology_NameIgnoreCaseAndNameIgnoreCase("biomass", "C4");
    }
}
