package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.dto.MiscGenerationDTO;
import com.rte_france.antares.datamanager_back.repository.model.MiscClusterCapacityEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MiscGenMapperTest {

    @Test
    void mapToMiscGenerationDTO_shouldMapHydrokineticToOther() {
        MiscClusterCapacityEntity entity = MiscClusterCapacityEntity.builder()
                .groupe("hydrokinetic")
                .capacityByYear(BigDecimal.valueOf(100.0))
                .build();

        MiscGenerationDTO dto = MiscGenMapper.mapToMiscGenerationDTO(entity);

        assertThat(dto.getGroupe()).isEqualTo("other");
        assertThat(dto.getCapacity()).isEqualTo(100.0);
    }

    @Test
    void mapToMiscGenerationDTO_shouldMapWaveToOther() {
        MiscClusterCapacityEntity entity = MiscClusterCapacityEntity.builder()
                .groupe("wave")
                .capacityByYear(BigDecimal.valueOf(200.0))
                .build();

        MiscGenerationDTO dto = MiscGenMapper.mapToMiscGenerationDTO(entity);

        assertThat(dto.getGroupe()).isEqualTo("other");
        assertThat(dto.getCapacity()).isEqualTo(200.0);
    }

    @Test
    void mapToMiscGenerationDTO_shouldKeepOtherGroups() {
        MiscClusterCapacityEntity entity = MiscClusterCapacityEntity.builder()
                .groupe("biomass")
                .capacityByYear(BigDecimal.valueOf(300.0))
                .build();

        MiscGenerationDTO dto = MiscGenMapper.mapToMiscGenerationDTO(entity);

        assertThat(dto.getGroupe()).isEqualTo("biomass");
        assertThat(dto.getCapacity()).isEqualTo(300.0);
    }

    @Test
    void mapToMiscGenerationDTO_shouldMapUnknownToOther() {
        MiscClusterCapacityEntity entity = MiscClusterCapacityEntity.builder()
                .groupe("unknown_group")
                .capacityByYear(BigDecimal.valueOf(400.0))
                .build();

        MiscGenerationDTO dto = MiscGenMapper.mapToMiscGenerationDTO(entity);

        assertThat(dto.getGroupe()).isEqualTo("other");
        assertThat(dto.getCapacity()).isEqualTo(400.0);
    }

    @Test
    void mapToMiscGenerationDTO_shouldMapNullToOther() {
        MiscClusterCapacityEntity entity = MiscClusterCapacityEntity.builder()
                .groupe(null)
                .capacityByYear(BigDecimal.valueOf(500.0))
                .build();

        MiscGenerationDTO dto = MiscGenMapper.mapToMiscGenerationDTO(entity);

        assertThat(dto.getGroupe()).isEqualTo("other");
        assertThat(dto.getCapacity()).isEqualTo(500.0);
    }
}
