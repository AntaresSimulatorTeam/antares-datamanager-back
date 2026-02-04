package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.dto.StStorageTrajectoryDataDTO;
import com.rte_france.antares.datamanager_back.repository.model.StStorageEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StStorageMapperTest {

    @Test
    void toStStorageTrajectoryDataDTO_SingleEntity_ShouldMapCorrectly() {
        // Given
        StStorageEntity stStorageEntity1 = StStorageEntity.builder().area("BE").name("battery_4").groupe("battery").series(true).build();

        // When
        StStorageTrajectoryDataDTO dto = StStorageMapper.toStStorageTrajectoryDataDTO(stStorageEntity1);

        // Then
        assertNotNull(dto);
        assertEquals("BE - battery - battery_4", dto.getCluster());
        assertEquals("TRUE", dto.getSeries());
    }

    @Test
    void toStStorageTrajectoryDataDTO_List_ShouldMapAllElements() {
        // Given
        StStorageEntity stStorageEntity1 = StStorageEntity.builder().area("BE").name("battery_4").groupe("battery").series(true).build();
        StStorageEntity stStorageEntity2 = StStorageEntity.builder().area("AT").name("dsr_shifting").groupe("dsr").series(true).build();

        List<StStorageEntity> stStorageEntities = List.of(stStorageEntity1, stStorageEntity2);

        // When
        List<StStorageTrajectoryDataDTO> dtoList = stStorageEntities.stream()
                .map(StStorageMapper::toStStorageTrajectoryDataDTO)
                .collect(Collectors.toList());

        // Then
        assertNotNull(dtoList);
        assertEquals(2, dtoList.size());
        assertEquals("BE - battery - battery_4", dtoList.get(0).getCluster());
        assertEquals("TRUE", dtoList.get(0).getSeries());
        assertEquals("AT - dsr - dsr_shifting", dtoList.get(1).getCluster());
        assertEquals("TRUE", dtoList.get(1).getSeries());
    }
    @Test
    void mapToStsGenerationDTO_ShouldMapAllFields() {
        // Given
        StStorageEntity entity = StStorageEntity.builder()
                .area("FR")
                .name("Storage1")
                .groupe("Group1")
                .enabled(true)
                .injection(new java.math.BigDecimal("10.5"))
                .withdrawal(new java.math.BigDecimal("5.2"))
                .storage(new java.math.BigDecimal("100.0"))
                .efficiencyInjection(new java.math.BigDecimal("0.9"))
                .efficiencyWithdrawal(80)
                .initialLevel(new java.math.BigDecimal("0.5"))
                .initialLevelOptim(true)
                .build();

        // When
        var dto = StStorageMapper.mapToStsGenerationDTO(entity);

        // Then
        assertNotNull(dto);
        assertEquals(true, dto.getEnabled());
        assertEquals("Group1", dto.getGroupe());
        assertEquals(10, dto.getInjection());
        assertEquals(5.2, dto.getWithdrawal());
        assertEquals(100.0, dto.getStorage());
        assertEquals(0.9, dto.getEfficiencyInjection());
        assertEquals(80.0, dto.getEfficiencyWithdrawal());
        assertEquals(0.5, dto.getInitialLevel());
        assertEquals(true, dto.getInitialLevelOptim());
    }

    @Test
    void mapToStsGenerationDTO_WithNullValues_ShouldHandleCorrectly() {
        // Given
        StStorageEntity entity = StStorageEntity.builder()
                .area("FR")
                .name("Storage1")
                .groupe("Group1")
                .enabled(null)
                .injection(null)
                .withdrawal(null)
                .storage(null)
                .efficiencyInjection(null)
                .efficiencyWithdrawal(null)
                .initialLevel(null)
                .initialLevelOptim(null)
                .build();

        // When
        var dto = StStorageMapper.mapToStsGenerationDTO(entity);

        // Then
        assertNotNull(dto);
        assertEquals(false, dto.getEnabled());
        assertEquals("Group1", dto.getGroupe());
        assertEquals(0, dto.getInjection());
        assertEquals(0.0, dto.getWithdrawal());
        assertEquals(0.0, dto.getStorage());
        assertEquals(0.0, dto.getEfficiencyInjection());
        assertEquals(0.0, dto.getEfficiencyWithdrawal());
        assertEquals(0.0, dto.getInitialLevel());
        assertEquals(false, dto.getInitialLevelOptim());
    }
}
