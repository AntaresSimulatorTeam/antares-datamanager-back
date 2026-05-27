package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.DsrGenerationDTO;
import com.rte_france.antares.datamanager_back.service.study.impl.DsrToJsonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DsrToJsonServiceTest {

    private DsrToJsonService dsrToJsonService;

    @BeforeEach
    void setUp() {
        dsrToJsonService = new DsrToJsonService();
    }

    @Test
    void buildDsrDataMap_ShouldReturnCorrectStructure() {
        // Given
        String areaName = "FR";
        DsrGenerationDTO dto = DsrGenerationDTO.builder()
                .enabled(true)
                .group("Other")
                .nominalCapacity(100.0)
                .unitCount(5)
                .marginalCost(50.0)
                .marketBidCost(50.0)
                .dsrTsList(List.of("ts1.txt", "ts2.txt"))
                .foDuration(2.5)
                .foMonthlyRate(Collections.nCopies(12, 0.1))
                .build();

        Map<String, DsrGenerationDTO> dsrClusterProps = Map.of("FR_Cluster1", dto);

        // When
        Map<String, Object> result = dsrToJsonService.buildDsrDataMap(areaName, dsrClusterProps);

        // Then
        assertEquals(1, result.size());
        assertTrue(result.containsKey("FR_Cluster1"));

        Map<String, Object> clusterData = (Map<String, Object>) result.get("FR_Cluster1");
        assertNotNull(clusterData);

        // Properties
        Map<String, Object> properties = (Map<String, Object>) clusterData.get("properties");
        assertNotNull(properties);
        assertEquals(true, properties.get("enabled"));
        assertEquals("Other", properties.get("group"));
        assertEquals(100.0, properties.get("nominal_capacity"));
        assertEquals(5, properties.get("unit_count"));
        assertEquals(50.0, properties.get("marginal_cost"));
        assertEquals(50.0, properties.get("market_bid_cost"));

        // Data
        Map<String, Object> data = (Map<String, Object>) clusterData.get("data");
        assertNotNull(data);
        assertEquals(2.5, data.get("fo_duration"));
        @SuppressWarnings("unchecked")
        List<Double> monthly = (List<Double>) data.get("fo_monthly_rate");
        assertNotNull(monthly);
        assertEquals(12, monthly.size());
        monthly.forEach(v -> assertEquals(0.1, v));

        // Modulation
        assertEquals(List.of("ts1.txt", "ts2.txt"), clusterData.get("modulation"));
    }

    @Test
    void buildDsrDataMap_ShouldReturnEmptyMapWhenNoPropsMatchArea() {
        // Given
        String areaName = "FR";
        DsrGenerationDTO dto = DsrGenerationDTO.builder().build();
        Map<String, DsrGenerationDTO> dsrClusterProps = Map.of("EN_Cluster1", dto);

        // When
        Map<String, Object> result = dsrToJsonService.buildDsrDataMap(areaName, dsrClusterProps);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void buildDsrDataMap_ShouldReturnEmptyMapWhenInputIsEmpty() {
        // When
        Map<String, Object> result = dsrToJsonService.buildDsrDataMap("FR", Collections.emptyMap());

        // Then
        assertTrue(result.isEmpty());
    }
}
