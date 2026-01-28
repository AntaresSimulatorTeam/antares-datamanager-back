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
}
