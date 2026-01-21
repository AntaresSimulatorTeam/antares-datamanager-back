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

import java.util.List;
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
        when(thermalClusterRefRepository.findByNamePemmdbIgnoreCase("PEM1"))
                .thenReturn(List.of());
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
    void updatePemmdb_overwritesWhenExistingIsNA_isNoLongerTrue() {
        ThermalTechnology tech = ThermalTechnology.builder().name("coal").build();
        ThermalClusterRef existing = ThermalClusterRef.builder()
                .id(11)
                .name("ClusterCoal")
                .thermalTechnology(tech)
                .namePemmdb("NA")
                .build();

        // Should NOT match because requested PEMMDB is "PEM-Y" but existing is "NA"
        when(thermalClusterRefRepository.findByThermalTechnology_NameIgnoreCaseAndNameIgnoreCase("coal", "ClusterCoal"))
                .thenReturn(Optional.of(existing));
        when(thermalClusterRefRepository.findByNamePemmdbIgnoreCase("PEM-Y"))
                .thenReturn(List.of());
        when(thermalTechnologyRepository.findThermalTechnologyByNameIgnoreCase("coal"))
                .thenReturn(Optional.of(tech));
        when(thermalClusterRefRepository.save(any()))
                .thenAnswer(inv -> {
                    ThermalClusterRef r = inv.getArgument(0);
                    r.setId(999);
                    return r;
                });

        ThermalClusterRef result =
                thermalClusterRef.findOrCreateThermalClusterRef("coal", "ClusterCoal", "PEM-Y");

        assertNotSame(existing, result);
        assertEquals(999, result.getId());
        assertEquals("PEM-Y", result.getNamePemmdb());
        verify(thermalClusterRefRepository).save(any());
    }

    @Test
    void updatePemmdb_setsWhenExistingIsNull_isNoLongerTrue() {
        ThermalTechnology tech = ThermalTechnology.builder().name("gas").build();
        ThermalClusterRef existing = ThermalClusterRef.builder()
                .id(10)
                .name("ClusterGas")
                .thermalTechnology(tech)
                .namePemmdb(null)
                .build();

        // Should NOT match because requested PEMMDB is "PEM-X" but existing is null (effectively NA)
        when(thermalClusterRefRepository.findByThermalTechnology_NameIgnoreCaseAndNameIgnoreCase("gas", "ClusterGas"))
                .thenReturn(Optional.of(existing));
        when(thermalClusterRefRepository.findByNamePemmdbIgnoreCase("PEM-X"))
                .thenReturn(List.of());
        when(thermalTechnologyRepository.findThermalTechnologyByNameIgnoreCase("gas"))
                .thenReturn(Optional.of(tech));
        when(thermalClusterRefRepository.save(any()))
                .thenAnswer(inv -> {
                    ThermalClusterRef r = inv.getArgument(0);
                    r.setId(888);
                    return r;
                });

        ThermalClusterRef result =
                thermalClusterRef.findOrCreateThermalClusterRef("gas", "ClusterGas", "PEM-X");

        assertNotSame(existing, result);
        assertEquals(888, result.getId());
        assertEquals("PEM-X", result.getNamePemmdb());
        verify(thermalClusterRefRepository).save(any());
    }


    @Test
    void findOrCreateThermalClusterRef_withoutTechnology_createsClusterWithNullTechnology() {
        when(thermalClusterRefRepository.findByThermalTechnologyIsNullAndNameIgnoreCase("ClusterX"))
                .thenReturn(List.of());
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

        // PEMMDB search should find it
        when(thermalClusterRefRepository.findByNamePemmdbIgnoreCase("PEM-OLD"))
                .thenReturn(List.of(existing));

        ThermalClusterRef result =
                thermalClusterRef.findOrCreateThermalClusterRef("hydro", "ClusterHydro", "PEM-OLD");

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
        when(thermalClusterRefRepository.findByNamePemmdbIgnoreCase("PEM-BIO"))
                .thenReturn(List.of());
        when(thermalTechnologyRepository.findThermalTechnologyByNameIgnoreCase("biomass"))
                .thenReturn(Optional.of(tech));
        when(thermalClusterRefRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        thermalClusterRef.findOrCreateThermalClusterRef("biomass", "  C4  ", "PEM-BIO");

        verify(thermalClusterRefRepository)
                .findByThermalTechnology_NameIgnoreCaseAndNameIgnoreCase("biomass", "C4");
    }

    @Test
    void findOrCreateThermalClusterRef_findsByPemmdbWhenNameLookupFails() {
        ThermalTechnology tech = ThermalTechnology.builder().name("nuclear").build();
        ThermalClusterRef existing = ThermalClusterRef.builder()
                .id(6)
                .name("WrongName")
                .thermalTechnology(tech)
                .namePemmdb("PEM-6")
                .build();

        // Initial lookup by name (with tech null as in processThermalSpecificRow) fails
        when(thermalClusterRefRepository.findByThermalTechnologyIsNullAndNameIgnoreCase("CorrectName"))
                .thenReturn(List.of());

        // Second lookup by PEMMDB succeeds
        when(thermalClusterRefRepository.findByNamePemmdbIgnoreCase("PEM-6"))
                .thenReturn(List.of(existing));

        ThermalClusterRef result = thermalClusterRef.findOrCreateThermalClusterRef(null, "CorrectName", "PEM-6");

        assertSame(existing, result);
        verify(thermalClusterRefRepository).findByThermalTechnologyIsNullAndNameIgnoreCase("CorrectName");
        verify(thermalClusterRefRepository).findByNamePemmdbIgnoreCase("PEM-6");
        verify(thermalClusterRefRepository, never()).save(any());
    }

    @Test
    void findOrCreateThermalClusterRef_findsByNameOnlyWhenTechIsNullAndPemmdbNull() {
        ThermalTechnology tech = ThermalTechnology.builder().name("gas").build();
        ThermalClusterRef existing = ThermalClusterRef.builder()
                .id(6)
                .name("Cluster6")
                .thermalTechnology(tech)
                .namePemmdb("NA")
                .build();

        // Initial lookup by name with tech null fails because we mock it to return empty
        when(thermalClusterRefRepository.findByThermalTechnologyIsNullAndNameIgnoreCase("Cluster6"))
                .thenReturn(List.of());

        // Name lookup (fallback) succeeds with a cluster that has PEMMDB="NA"
        when(thermalClusterRefRepository.findByNameIgnoreCase("Cluster6"))
                .thenReturn(List.of(existing));

        ThermalClusterRef result = thermalClusterRef.findOrCreateThermalClusterRef(null, "Cluster6", null);

        assertSame(existing, result);
        verify(thermalClusterRefRepository).findByThermalTechnologyIsNullAndNameIgnoreCase("Cluster6");
        verify(thermalClusterRefRepository).findByNameIgnoreCase("Cluster6");
        verify(thermalClusterRefRepository, never()).save(any());
    }

    @Test
    void findOrCreateThermalClusterRef_createsNewWhenMultipleClustersWithSameNameExist() {
        ThermalClusterRef c1 = ThermalClusterRef.builder().id(1).name("C").build();
        ThermalClusterRef c2 = ThermalClusterRef.builder().id(2).name("C").build();

        when(thermalClusterRefRepository.findByThermalTechnologyIsNullAndNameIgnoreCase("C"))
                .thenReturn(List.of());
        when(thermalClusterRefRepository.findByNameIgnoreCase("C"))
                .thenReturn(List.of(c1, c2));
        when(thermalClusterRefRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        ThermalClusterRef result = thermalClusterRef.findOrCreateThermalClusterRef(null, "C", null);

        assertNotSame(c1, result);
        assertNotSame(c2, result);
        assertEquals("C", result.getName());
        assertNull(result.getThermalTechnology());
        verify(thermalClusterRefRepository).save(any());
    }

    @Test
    void findOrCreateThermalClusterRef_ignoresPemmdbLookupWhenPemmdbIsNA() {
        when(thermalClusterRefRepository.findByThermalTechnologyIsNullAndNameIgnoreCase("ClusterX"))
                .thenReturn(List.of());
        // findByNameIgnoreCase (fallback) also empty
        when(thermalClusterRefRepository.findByNameIgnoreCase("ClusterX"))
                .thenReturn(List.of());
        when(thermalClusterRefRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        ThermalClusterRef result = thermalClusterRef.findOrCreateThermalClusterRef(null, "ClusterX", "NA");

        assertNotNull(result);
        assertEquals("ClusterX", result.getName());
        assertEquals("NA", result.getNamePemmdb());
        verify(thermalClusterRefRepository, never()).findByNamePemmdbIgnoreCase(anyString());
    }

    @Test
    void findOrCreateThermalClusterRef_findsByPemmdbWhenPemmdbIsProvidedAndTechIsNull() {
        ThermalTechnology tech = ThermalTechnology.builder().name("gas").build();
        ThermalClusterRef existing = ThermalClusterRef.builder()
                .id(100)
                .name("OtherName")
                .thermalTechnology(tech)
                .namePemmdb("PEM-100")
                .build();

        when(thermalClusterRefRepository.findByThermalTechnologyIsNullAndNameIgnoreCase("SomeName"))
                .thenReturn(List.of());
        when(thermalClusterRefRepository.findByNamePemmdbIgnoreCase("PEM-100"))
                .thenReturn(List.of(existing));

        ThermalClusterRef result = thermalClusterRef.findOrCreateThermalClusterRef(null, "SomeName", "PEM-100");

        assertSame(existing, result);
        verify(thermalClusterRefRepository).findByNamePemmdbIgnoreCase("PEM-100");
    }

    @Test
    void findOrCreateThermalClusterRef_correctlyDistinguishesBetweenPemmdbAndNonPemmdbRows() {
        // Mock existing cluster with Name="CCGT" and PEMMDB="NA"
        ThermalClusterRef clusterNA = ThermalClusterRef.builder()
                .id(1)
                .name("CCGT")
                .namePemmdb("NA")
                .build();

        // Row 1: Name="CCGT", PEMMDB="" (should match clusterNA)
        when(thermalClusterRefRepository.findByThermalTechnologyIsNullAndNameIgnoreCase("CCGT"))
                .thenReturn(List.of(clusterNA));

        ThermalClusterRef result1 = thermalClusterRef.findOrCreateThermalClusterRef(null, "CCGT", "");
        assertSame(clusterNA, result1);

        // Row 2: Name="CCGT", PEMMDB="SOME_VAL" (should NOT match clusterNA, but find or create new)
        // We use a different mock object instance for the second call to isolate it
        ThermalClusterRef clusterNA2 = ThermalClusterRef.builder()
                .id(1)
                .name("CCGT")
                .namePemmdb("NA")
                .build();
        reset(thermalClusterRefRepository, thermalTechnologyRepository);
        when(thermalClusterRefRepository.findByThermalTechnologyIsNullAndNameIgnoreCase("CCGT"))
                .thenReturn(List.of(clusterNA2));
        when(thermalClusterRefRepository.findByNamePemmdbIgnoreCase("SOME_VAL"))
                .thenReturn(List.of()); // Assume SOME_VAL doesn't exist yet
        when(thermalClusterRefRepository.save(any()))
                .thenAnswer(inv -> {
                    ThermalClusterRef saved = inv.getArgument(0);
                    saved.setId(999); // Give it a different ID
                    return saved;
                });

        ThermalClusterRef result2 = thermalClusterRef.findOrCreateThermalClusterRef(null, "CCGT", "SOME_VAL");

        assertNotSame(clusterNA2, result2);
        assertEquals(999, result2.getId());
        assertEquals("SOME_VAL", result2.getNamePemmdb());
        // Verify clusterNA2 was NOT modified
        assertEquals("NA", clusterNA2.getNamePemmdb());
    }

    @Test
    void findOrCreateThermalClusterRef_handlesMultipleResultsFromRepository() {
        ThermalClusterRef c1 = ThermalClusterRef.builder().id(1).name("CCGT").namePemmdb("PEM1").build();
        ThermalClusterRef c2 = ThermalClusterRef.builder().id(2).name("CCGT").namePemmdb("PEM2").build();

        // Simulate 2 clusters with same name and no technology in DB
        when(thermalClusterRefRepository.findByThermalTechnologyIsNullAndNameIgnoreCase("CCGT"))
                .thenReturn(List.of(c1, c2));

        // Looking for CCGT with PEM2 should return c2
        ThermalClusterRef result = thermalClusterRef.findOrCreateThermalClusterRef(null, "CCGT", "PEM2");
        assertSame(c2, result);

        // Looking for CCGT with PEM1 should return c1
        result = thermalClusterRef.findOrCreateThermalClusterRef(null, "CCGT", "PEM1");
        assertSame(c1, result);

        // Looking for CCGT with PEM3 should return empty from techNullRefs and then try findByNamePemmdbIgnoreCase
        when(thermalClusterRefRepository.findByNamePemmdbIgnoreCase("PEM3"))
                .thenReturn(List.of());
        when(thermalClusterRefRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        result = thermalClusterRef.findOrCreateThermalClusterRef(null, "CCGT", "PEM3");
        assertNotSame(c1, result);
        assertNotSame(c2, result);
        assertEquals("PEM3", result.getNamePemmdb());
    }
}
