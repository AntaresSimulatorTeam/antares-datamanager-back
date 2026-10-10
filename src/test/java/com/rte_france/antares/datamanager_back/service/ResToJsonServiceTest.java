package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.ResClusterGenerationDto;
import com.rte_france.antares.datamanager_back.dto.ResClusterPropertiesDto;
import com.rte_france.antares.datamanager_back.service.study.impl.ResToJsonService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResToJsonServiceTest {

    private final ResToJsonService service = new ResToJsonService();

    @Test
    void buildResDataMap_shouldReturnEmpty_whenInputIsNull() {
        var result = service.buildResDataMap("FR", null);
        assertTrue(result.isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"DE"})
    void buildResDataMap_shouldReturnEmpty_whenAreaNotFound(String areaName) {
        Map<String, Map<String, ResClusterGenerationDto>> input = Map.of("FR", Map.of());
        var result = service.buildResDataMap(areaName, input);
        assertTrue(result.isEmpty());
    }

    @Test
    void buildResDataMap_shouldReturnAreaMap_whenAreaExists() {
        var cluster = new ResClusterGenerationDto(
                new ResClusterPropertiesDto(1200.0, "wind_onshore"),
                List.of("wind_FR01_onshore_2030-2031.csv.arrow"),
                null
        );
        Map<String, ResClusterGenerationDto> areaMap = Map.of("wind_onshore", cluster);
        var result = service.buildResDataMap("fr", Map.of("FR", areaMap));
        assertEquals(areaMap, result);
    }

    @Test
    void buildResDataMap_shouldReturnEmpty_whenAreaMapEmpty() {
        Map<String, Map<String, ResClusterGenerationDto>> input = Map.of("FR", Collections.emptyMap());
        var result = service.buildResDataMap("FR", input);
        assertTrue(result.isEmpty());
    }
}
