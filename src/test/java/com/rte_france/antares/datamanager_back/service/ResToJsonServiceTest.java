package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.service.study.impl.ResToJsonService;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResToJsonServiceTest {

    private final ResToJsonService service = new ResToJsonService();

    @Test
    void buildResDataMap_shouldReturnEmpty_whenInputIsNull() {
        Map<String, Object> result = service.buildResDataMap("FR", null);
        assertTrue(result.isEmpty());
    }

    @Test
    void buildResDataMap_shouldReturnEmpty_whenAreaIsMissing() {
        Map<String, Object> result = service.buildResDataMap("", Map.of("FR", Map.of("k", "v")));
        assertTrue(result.isEmpty());
    }

    @Test
    void buildResDataMap_shouldReturnEmpty_whenAreaNotFound() {
        Map<String, Object> result = service.buildResDataMap("DE", Map.of("FR", Map.of("k", "v")));
        assertTrue(result.isEmpty());
    }

    @Test
    void buildResDataMap_shouldReturnAreaMap_whenAreaExists() {
        Map<String, Object> areaMap = Map.of("wind_onshore", Map.of(
                "properties", Map.of("group", "wind_onshore", "capacity", 1200.0),
                "series", Collections.singletonList("wind_FR01_onshore_2030-2031.csv.arrow")
        ));
        Map<String, Object> result = service.buildResDataMap("fr", Map.of("FR", areaMap));
        assertEquals(areaMap, result);
    }

    @Test
    void buildResDataMap_shouldReturnEmpty_whenAreaMapEmpty() {
        Map<String, Object> result = service.buildResDataMap("FR", Map.of("FR", Collections.emptyMap()));
        assertTrue(result.isEmpty());
    }
}

