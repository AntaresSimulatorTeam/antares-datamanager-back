package com.rte_france.antares.datamanager_back.service.study.impl;

import com.rte_france.antares.datamanager_back.dto.MiscGenerationDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MiscToJsonServiceTest {

    private MiscToJsonService miscToJsonService;

    @BeforeEach
    void setUp() {
        miscToJsonService = new MiscToJsonService();
    }

    @Test
    void buildMiscDataMap_shouldReturnEmptyMapWhenInputIsNull() {
        Map<String, Object> result = miscToJsonService.buildMiscDataMap("FR", null);
        assertTrue(result.isEmpty());
    }

    @Test
    void buildMiscDataMap_shouldReturnEmptyMapWhenInputIsEmpty() {
        Map<String, Object> result = miscToJsonService.buildMiscDataMap("FR", new HashMap<>());
        assertTrue(result.isEmpty());
    }

    @Test
    void buildMiscDataMap_shouldReturnEmptyMapWhenNoDataForArea() {
        Map<String, List<MiscGenerationDTO>> miscProps = new HashMap<>();
        miscProps.put("BE", List.of(MiscGenerationDTO.builder().groupe("biomass").build()));

        Map<String, Object> result = miscToJsonService.buildMiscDataMap("FR", miscProps);
        assertTrue(result.isEmpty());
    }

    @Test
    void buildMiscDataMap_shouldOrganizeByGroup() {
        // Given
        Map<String, List<MiscGenerationDTO>> miscProps = new HashMap<>();
        miscProps.put("FR", List.of(
                MiscGenerationDTO.builder()
                        .capacity(100.0)
                        .groupe("biogas")
                        .miscGenTsList(List.of("FR_biogas.UUID.arrow", "FR_biomass.UUID.arrow"))
                        .build(),
                MiscGenerationDTO.builder()
                        .capacity(50.0)
                        .groupe("biomass")
                        .miscGenTsList(List.of("FR_biogas.UUID.arrow", "FR_biomass.UUID.arrow"))
                        .build()
        ));

        // When
        Map<String, Object> result = miscToJsonService.buildMiscDataMap("FR", miscProps);

        // Then
        assertEquals(2, result.size());
        assertTrue(result.containsKey("biogas"));
        assertTrue(result.containsKey("biomass"));

        Map<String, Object> biogasData = (Map<String, Object>) result.get("biogas");
        Map<String, Object> biogasProps = (Map<String, Object>) biogasData.get("properties");
        assertEquals(100.0, biogasProps.get("capacity"));
        assertEquals("biogas", biogasProps.get("group"));
        assertEquals(List.of("FR_biogas.UUID.arrow"), biogasData.get("series"));

        Map<String, Object> biomassData = (Map<String, Object>) result.get("biomass");
        Map<String, Object> biomassProps = (Map<String, Object>) biomassData.get("properties");
        assertEquals(50.0, biomassProps.get("capacity"));
        assertEquals("biomass", biomassProps.get("group"));
        assertEquals(List.of("FR_biomass.UUID.arrow"), biomassData.get("series"));
    }

    @Test
    void buildMiscDataMap_shouldFilterSeriesByGroupCaseInsensitive() {
        // Given
        Map<String, List<MiscGenerationDTO>> miscProps = new HashMap<>();
        miscProps.put("FR", List.of(
                MiscGenerationDTO.builder()
                        .capacity(100.0)
                        .groupe("Biogas")
                        .miscGenTsList(List.of("FR_BIOGAS.UUID.arrow", "FR_other.UUID.arrow"))
                        .build()
        ));

        // When
        Map<String, Object> result = miscToJsonService.buildMiscDataMap("FR", miscProps);

        // Then
        Map<String, Object> biogasData = (Map<String, Object>) result.get("Biogas");
        assertEquals(List.of("FR_BIOGAS.UUID.arrow"), biogasData.get("series"));
    }

    @Test
    void buildMiscDataMap_shouldMergeOtherWaveAndHydrokineticIntoOther() {
        Map<String, List<MiscGenerationDTO>> miscProps = new HashMap<>();
        miscProps.put("FR", List.of(
                MiscGenerationDTO.builder()
                        .capacity(10.0)
                        .groupe("other")
                        .miscGenTsList(List.of("FR_other.UUID.arrow", "FR_wave.UUID.arrow", "FR_hydrokinetic.UUID.arrow"))
                        .build(),
                MiscGenerationDTO.builder()
                        .capacity(20.0)
                        .groupe("other")
                        .miscGenTsList(List.of("FR_other.UUID.arrow", "FR_wave.UUID.arrow", "FR_hydrokinetic.UUID.arrow"))
                        .build(),
                MiscGenerationDTO.builder()
                        .capacity(30.0)
                        .groupe("other")
                        .miscGenTsList(List.of("FR_other.UUID.arrow", "FR_wave.UUID.arrow", "FR_hydrokinetic.UUID.arrow"))
                        .build()
        ));

        Map<String, Object> result = miscToJsonService.buildMiscDataMap("FR", miscProps);

        assertEquals(1, result.size());
        assertTrue(result.containsKey("other"));

        Map<String, Object> otherData = (Map<String, Object>) result.get("other");
        Map<String, Object> otherProps = (Map<String, Object>) otherData.get("properties");
        assertEquals(60.0, otherProps.get("capacity"));
        assertEquals("other", otherProps.get("group"));
        assertEquals(List.of("FR_other.UUID.arrow", "FR_wave.UUID.arrow", "FR_hydrokinetic.UUID.arrow"), otherData.get("series"));
    }
}
