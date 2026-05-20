package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.HydroGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.HydroAllocationEntity;
import com.rte_france.antares.datamanager_back.repository.model.HydroParametersEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.hydro.impl.HydroGenerationAssemblerServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class HydroGenerationAssemblerServiceImplTest {

    @InjectMocks
    private HydroGenerationAssemblerServiceImpl service;

    @Test
    void assembleHydroProperties_returnsGroupedProperties() {
        // Given
        HydroParametersEntity hp1 = HydroParametersEntity.builder()
                .node("FR")
                .followLoad(true)
                .interDailyBreakdown(1)
                .reservoirCapacity(new BigDecimal(1000))
                .build();
        HydroParametersEntity hp2 = HydroParametersEntity.builder()
                .node("BE")
                .followLoad(false)
                .interDailyBreakdown(2)
                .reservoirCapacity(new BigDecimal(2000))
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp1, hp2))
                .build();

        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        // When
        Map<String, List<HydroGenerationDTO>> result = service.assembleHydroProperties(studyEntity);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.containsKey("FR"));
        assertTrue(result.containsKey("BE"));

        List<HydroGenerationDTO> frProps = result.get("FR");
        assertEquals(1, frProps.size());
        assertEquals(Boolean.TRUE, frProps.get(0).getFollowLoadModulation());
        assertEquals(1000, frProps.get(0).getReservoirCapacity());

        List<HydroGenerationDTO> beProps = result.get("BE");
        assertEquals(1, beProps.size());
        assertEquals(Boolean.FALSE, beProps.get(0).getFollowLoadModulation());
        assertEquals(2000, beProps.get(0).getReservoirCapacity());
    }

    @Test
    void assembleHydroProperties_filtersOutOtherTrajectoryTypes() {
        // Given
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.AREA.name())
                .build();

        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        // When
        Map<String, List<HydroGenerationDTO>> result = service.assembleHydroProperties(studyEntity);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void assembleHydroProperties_includesAllocations() {
        // Given
        HydroParametersEntity hp1 = HydroParametersEntity.builder()
                .node("FR")
                .followLoad(true)
                .build();

        TrajectoryEntity trajParams = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp1))
                .build();

        HydroAllocationEntity ha1 = HydroAllocationEntity.builder()
                .hydro("FR")
                .load("AT")
                .allocation(1)
                .build();
        HydroAllocationEntity ha2 = HydroAllocationEntity.builder()
                .hydro("FR")
                .load("DE")
                .allocation(2)
                .build();

        TrajectoryEntity trajAlloc = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_ALLOCATION.name())
                .hydroAllocationEntities(List.of(ha1, ha2))
                .build();

        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(trajParams, trajAlloc))
                .build();

        // When
        Map<String, List<HydroGenerationDTO>> result = service.assembleHydroProperties(studyEntity);

        // Then
        assertNotNull(result);
        assertTrue(result.containsKey("FR"));
        List<HydroGenerationDTO> frDto = result.get("FR");
        assertEquals(1, frDto.size());
        Map<String, Double> allocation = frDto.get(0).getAllocation();
        assertNotNull(allocation);
        assertEquals(2, allocation.size());
        assertEquals(1.0, allocation.get("AT"));
        assertEquals(2.0, allocation.get("DE"));
    }
}
