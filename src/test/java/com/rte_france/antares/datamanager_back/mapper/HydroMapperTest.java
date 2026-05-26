package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.dto.HydroGenerationDTO;
import com.rte_france.antares.datamanager_back.repository.model.HydroParametersEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class HydroMapperTest {

    @Test
    void mapToHydroGenerationDTO_shouldReturnNullWhenEntityIsNull() {
        HydroGenerationDTO result = HydroMapper.mapToHydroGenerationDTO(null);

        assertThat(result).isNull();
    }

    @Test
    void mapToHydroGenerationDTO_shouldMapAllFields() {
        HydroParametersEntity entity = HydroParametersEntity.builder()
                .followLoad(true)
                .interDailyBreakdown(1)
                .interDailyModulation(2)
                .interMonthlyBreakdown(3)
                .reservoir(true)
                .reservoirCapacity(new BigDecimal("5000"))
                .pumpingEfficiency(90)
                .initializeReservoirDate(7)
                .useWater(false)
                .build();

        HydroGenerationDTO result = HydroMapper.mapToHydroGenerationDTO(entity);

        assertThat(result).isNotNull();
        assertThat(result.getFollowLoadModulation()).isTrue();
        assertThat(result.getInterDailyBreakdown()).isEqualTo(1);
        assertThat(result.getInterDailyModulation()).isEqualTo(2);
        assertThat(result.getInterMonthlyBreakdown()).isEqualTo(3);
        assertThat(result.getReservoirManagement()).isTrue();
        assertThat(result.getReservoirCapacity()).isEqualTo(5000);
        assertThat(result.getPumpingEfficiency()).isEqualTo(90);
        assertThat(result.getInitializeReservoirDate()).isEqualTo(7);
        assertThat(result.getUseWater()).isFalse();
    }

    @Test
    void mapToHydroGenerationDTO_shouldReturnNullReservoirCapacityWhenEntityFieldIsNull() {
        HydroParametersEntity entity = HydroParametersEntity.builder()
                .node("FR")
                .reservoirCapacity(null)
                .build();

        HydroGenerationDTO result = HydroMapper.mapToHydroGenerationDTO(entity);

        assertThat(result).isNotNull();
        assertThat(result.getReservoirCapacity()).isNull();
    }

    @Test
    void mapToHydroGenerationDTO_shouldTruncateReservoirCapacityDecimal() {
        HydroParametersEntity entity = HydroParametersEntity.builder()
                .reservoirCapacity(new BigDecimal("1999.99"))
                .build();

        HydroGenerationDTO result = HydroMapper.mapToHydroGenerationDTO(entity);

        assertThat(result.getReservoirCapacity()).isEqualTo(1999);
    }

    @Test
    void mapToHydroGenerationDTO_shouldNotMapAllocationNorSeries() {
        HydroParametersEntity entity = HydroParametersEntity.builder()
                .node("FR")
                .followLoad(true)
                .build();

        HydroGenerationDTO result = HydroMapper.mapToHydroGenerationDTO(entity);

        assertThat(result.getAllocation()).isNull();
        assertThat(result.getSeries()).isNull();
    }

    @Test
    void mapToHydroGenerationDTO_shouldMapNullBooleanFields() {
        HydroParametersEntity entity = HydroParametersEntity.builder()
                .followLoad(null)
                .reservoir(null)
                .useWater(null)
                .build();

        HydroGenerationDTO result = HydroMapper.mapToHydroGenerationDTO(entity);

        assertThat(result.getFollowLoadModulation()).isNull();
        assertThat(result.getReservoirManagement()).isNull();
        assertThat(result.getUseWater()).isNull();
    }

    @Test
    void mapToHydroGenerationDTO_shouldMapAllNullFields() {
        HydroParametersEntity entity = HydroParametersEntity.builder().build();

        HydroGenerationDTO result = HydroMapper.mapToHydroGenerationDTO(entity);

        assertThat(result).isNotNull();
        assertThat(result.getFollowLoadModulation()).isNull();
        assertThat(result.getInterDailyBreakdown()).isNull();
        assertThat(result.getInterDailyModulation()).isNull();
        assertThat(result.getInterMonthlyBreakdown()).isNull();
        assertThat(result.getReservoirManagement()).isNull();
        assertThat(result.getReservoirCapacity()).isNull();
        assertThat(result.getPumpingEfficiency()).isNull();
        assertThat(result.getInitializeReservoirDate()).isNull();
        assertThat(result.getUseWater()).isNull();
    }
}
