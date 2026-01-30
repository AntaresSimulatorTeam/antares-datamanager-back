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
    private ThermalClusterRefServiceImpl service;

    @Test
    void returnsExistingCluster_whenExactMatchOnNamePemmdbAndTech() {
        ThermalTechnology tech = ThermalTechnology.builder().name("CCGT").build();
        ThermalClusterRef existing = ThermalClusterRef.builder()
                .name("C1")
                .namePemmdb("PEM1")
                .thermalTechnology(tech)
                .build();

        when(thermalTechnologyRepository.findThermalTechnologyByNameIgnoreCase("CCGT"))
                .thenReturn(Optional.of(tech));

        when(thermalClusterRefRepository
                .findByNameIgnoreCaseAndNamePemmdbIgnoreCaseAndThermalTechnology("C1", "PEM1", tech))
                .thenReturn(Optional.of(existing));

        ThermalClusterRef result =
                service.findOrCreateThermalClusterRef("CCGT", "C1", "PEM1");

        assertSame(existing, result);
        verify(thermalClusterRefRepository, never()).save(any());
    }

    @Test
    void createsCluster_whenNoExactMatchExists() {
        ThermalTechnology tech = ThermalTechnology.builder().name("CCGT").build();

        when(thermalTechnologyRepository.findThermalTechnologyByNameIgnoreCase("CCGT"))
                .thenReturn(Optional.of(tech));

        when(thermalClusterRefRepository
                .findByNameIgnoreCaseAndNamePemmdbIgnoreCaseAndThermalTechnology("C1", "PEM1", tech))
                .thenReturn(Optional.empty());

        when(thermalClusterRefRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        ThermalClusterRef result =
                service.findOrCreateThermalClusterRef("CCGT", "C1", "PEM1");

        assertEquals("C1", result.getName());
        assertEquals("PEM1", result.getNamePemmdb());
        assertEquals(tech, result.getThermalTechnology());
    }

    @Test
    void normalizesNullPemmdbToNA() {
        ThermalTechnology tech = ThermalTechnology.builder().name("nuclear").build();

        when(thermalTechnologyRepository.findThermalTechnologyByNameIgnoreCase("nuclear"))
                .thenReturn(Optional.of(tech));

        when(thermalClusterRefRepository
                .findByNameIgnoreCaseAndNamePemmdbIgnoreCaseAndThermalTechnology("C2", "NA", tech))
                .thenReturn(Optional.empty());

        when(thermalClusterRefRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        ThermalClusterRef result =
                service.findOrCreateThermalClusterRef("nuclear", "C2", null);

        assertEquals("NA", result.getNamePemmdb());
    }

    @Test
    void supportsNullTechnology() {
        when(thermalClusterRefRepository
                .findByNameIgnoreCaseAndNamePemmdbIgnoreCaseAndThermalTechnology("ClusterX", "PEM-NULL", null))
                .thenReturn(Optional.empty());

        when(thermalClusterRefRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        ThermalClusterRef result =
                service.findOrCreateThermalClusterRef(null, "ClusterX", "PEM-NULL");

        assertNull(result.getThermalTechnology());
        verifyNoInteractions(thermalTechnologyRepository);
    }

    @Test
    void trimsClusterNameBeforeLookup() {
        ThermalTechnology tech = ThermalTechnology.builder().name("bio").build();

        when(thermalTechnologyRepository.findThermalTechnologyByNameIgnoreCase("bio"))
                .thenReturn(Optional.of(tech));

        when(thermalClusterRefRepository
                .findByNameIgnoreCaseAndNamePemmdbIgnoreCaseAndThermalTechnology("C4", "PEM", tech))
                .thenReturn(Optional.empty());

        service.findOrCreateThermalClusterRef("bio", "  C4  ", "PEM");

        verify(thermalClusterRefRepository)
                .findByNameIgnoreCaseAndNamePemmdbIgnoreCaseAndThermalTechnology("C4", "PEM", tech);
    }

    @Test
    void throwsBusinessException_whenTechnologyDoesNotExist() {
        when(thermalTechnologyRepository.findThermalTechnologyByNameIgnoreCase("unknown"))
                .thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> service.findOrCreateThermalClusterRef("unknown", "C3", "PEM-Z"));

        verifyNoInteractions(thermalClusterRefRepository);
    }

    @Test
    void returnsExistingCluster_whenTechIsNullAndMatchExists() {
        ThermalClusterRef existing = ThermalClusterRef.builder()
                .name("Gas pcomp mid")
                .namePemmdb("NA")
                .thermalTechnology(null)
                .build();

        when(thermalClusterRefRepository
                .findByNameIgnoreCaseAndNamePemmdbIgnoreCaseAndThermalTechnology("Gas pcomp mid", "NA", null))
                .thenReturn(Optional.of(existing));

        ThermalClusterRef result =
                service.findOrCreateThermalClusterRef(null, "Gas pcomp mid", null);

        assertSame(existing, result);
    }

}
