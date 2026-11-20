package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.dto.AreaDTO;
import com.rte_france.antares.datamanager_back.repository.model.AreaConfigEntity;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AreaMapperTest {

    @Test
    void toAreaDtoMapsCoordinatesColorsAndCosts() {
        AreaEntity area = AreaEntity.builder()
                .name("fr").x(12.5).y(-34.5).r(255.0).g(128.0).b(0.0).build();
        AreaConfigEntity config = AreaConfigEntity.builder()
                .area(area).unsuppliedEnergyCost(1000.0).spilledEnergyCost(10.0).build();

        AreaDTO dto = AreaMapper.toAreaDto(config);

        assertThat(dto.getName()).isEqualTo("FR");
        assertThat(dto.getX()).isEqualTo(12.5);
        assertThat(dto.getY()).isEqualTo("-34.5");
        assertThat(dto.getR()).isEqualTo("255.0");
        assertThat(dto.getG()).isEqualTo("128.0");
        assertThat(dto.getB()).isEqualTo("0.0");
        assertThat(dto.getUnsuppliedEnergyCost()).isEqualTo("1000.0");
        assertThat(dto.getSpilledEnergyCost()).isEqualTo("10.0");
    }

    @Test
    void toAreaDtoPreservesNullCoordinatesAndColors() {
        AreaConfigEntity config = AreaConfigEntity.builder()
                .area(AreaEntity.builder().name("fr").build()).build();

        AreaDTO dto = AreaMapper.toAreaDto(config);

        assertThat(dto.getX()).isNull();
        assertThat(dto.getY()).isNull();
        assertThat(dto.getR()).isNull();
        assertThat(dto.getG()).isNull();
        assertThat(dto.getB()).isNull();
    }
}
