package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.StsGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.StStorageEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.sts.StsPropertiesAssemblerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StsPropertiesAssemblerServiceImplTest {

    private StsPropertiesAssemblerServiceImpl stsPropertiesAssemblerService;

    @BeforeEach
    void setUp() {
        stsPropertiesAssemblerService = new StsPropertiesAssemblerServiceImpl();
    }

    @Test
    void assembleStsProperties_ShouldReturnMappedProperties() {
        // Given
        StStorageEntity stStorage1 = StStorageEntity.builder()
                .area("FR")
                .name("Storage1")
                .group("Group1")
                .injection(new BigDecimal("10.5"))
                .withdrawal(new BigDecimal("5.2"))
                .storage(new BigDecimal("100.0"))
                .efficiencyInjection(new BigDecimal("0.9"))
                .efficiencyWithdrawal(80) // StStorageEntity has Integer for efficiencyWithdrawal
                .initialLevel(new BigDecimal("0.5"))
                .initialLevelOptim(true)
                .enabled(true)
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.STS.name())
                .stStorageEntities(List.of(stStorage1))
                .build();

        StudyEntity study = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        // When
        Map<String, StsGenerationDTO> result = stsPropertiesAssemblerService.assembleStsProperties(study);

        // Then
        assertEquals(1, result.size());
        assertTrue(result.containsKey("FR_Storage1"));
        StsGenerationDTO dto = result.get("FR_Storage1");
        assertEquals(true, dto.getEnabled());
        assertEquals("Group1", dto.getGroup());
        assertEquals(10, dto.getInjection());
        assertEquals(5.2, dto.getWithdrawal());
        assertEquals(100.0, dto.getStorage());
        assertEquals(0.9, dto.getEfficiencyInjection());
        assertEquals(80.0, dto.getEfficiencyWithdrawal());
        assertEquals(0.5, dto.getInitialLevel());
        assertEquals(true, dto.getInitialLevelOptim());
    }

    @Test
    void assembleStsProperties_ShouldHandleMultipleTrajectoriesAndAreas() {
        // Given
        StStorageEntity stStorage1 = StStorageEntity.builder()
                .area("fr")
                .name("S1")
                .enabled(true)
                .injection(BigDecimal.ONE)
                .build();
        TrajectoryEntity traj1 = TrajectoryEntity.builder()
                .type(TrajectoryType.STS.name())
                .stStorageEntities(List.of(stStorage1))
                .build();

        StStorageEntity stStorage2 = StStorageEntity.builder()
                .area("be")
                .name("S2")
                .enabled(false)
                .withdrawal(BigDecimal.ONE)
                .build();
        TrajectoryEntity traj2 = TrajectoryEntity.builder()
                .type(TrajectoryType.STS.name())
                .stStorageEntities(List.of(stStorage2))
                .build();

        StudyEntity study = StudyEntity.builder()
                .trajectories(Set.of(traj1, traj2))
                .build();

        // When
        Map<String, StsGenerationDTO> result = stsPropertiesAssemblerService.assembleStsProperties(study);

        // Then
        assertEquals(2, result.size());
        assertTrue(result.containsKey("FR_S1"));
        assertTrue(result.containsKey("BE_S2"));
        assertEquals(true, result.get("FR_S1").getEnabled());
        assertEquals(false, result.get("BE_S2").getEnabled());
    }

    @Test
    void assembleStsProperties_ShouldSkipNonStsTrajectories() {
        // Given
        TrajectoryEntity traj1 = TrajectoryEntity.builder()
                .type(TrajectoryType.AREA.name())
                .stStorageEntities(List.of(StStorageEntity.builder().area("FR").name("S1").build()))
                .build();

        StudyEntity study = StudyEntity.builder()
                .trajectories(Set.of(traj1))
                .build();

        // When
        Map<String, StsGenerationDTO> result = stsPropertiesAssemblerService.assembleStsProperties(study);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void assembleStsProperties_ShouldHandleNullFields() {
        // Given
        StStorageEntity stStorage = StStorageEntity.builder()
                .area("FR")
                .name("S1")
                .storage(BigDecimal.TEN)
                .build();
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.STS.name())
                .stStorageEntities(List.of(stStorage))
                .build();
        StudyEntity study = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        // When
        Map<String, StsGenerationDTO> result = stsPropertiesAssemblerService.assembleStsProperties(study);

        // Then
        StsGenerationDTO dto = result.get("FR_S1");
        assertEquals(false, dto.getEnabled());
        assertEquals(0, dto.getInjection());
        assertEquals(0.0, dto.getWithdrawal());
        assertEquals(false, dto.getInitialLevelOptim());
    }

    @Test
    void assembleStsProperties_ShouldExcludeClustersWithZeroTotalCapacity() {
        // Given
        StStorageEntity stStorageZero = StStorageEntity.builder()
                .area("FR")
                .name("ZeroCapacity")
                .injection(BigDecimal.ZERO)
                .withdrawal(BigDecimal.ZERO)
                .storage(BigDecimal.ZERO)
                .build();

        StStorageEntity stStoragePartial = StStorageEntity.builder()
                .area("FR")
                .name("PartialCapacity")
                .injection(BigDecimal.ZERO)
                .withdrawal(new BigDecimal("1.0"))
                .storage(BigDecimal.ZERO)
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.STS.name())
                .stStorageEntities(List.of(stStorageZero, stStoragePartial))
                .build();

        StudyEntity study = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        // When
        Map<String, StsGenerationDTO> result = stsPropertiesAssemblerService.assembleStsProperties(study);

        // Then
        assertEquals(1, result.size());
        assertTrue(result.containsKey("FR_PartialCapacity"));
        assertFalse(result.containsKey("FR_ZeroCapacity"));
    }
}
