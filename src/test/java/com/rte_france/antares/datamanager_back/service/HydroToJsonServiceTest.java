package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.HydroGenerationDTO;
import com.rte_france.antares.datamanager_back.service.study.impl.HydroToJsonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HydroToJsonServiceTest {

    private HydroToJsonService service;

    @BeforeEach
    void setUp() {
        service = new HydroToJsonService();
    }

    @Test
    void buildHydroDataMap_returnsEmptyMapWhenHydroPropsByAreaIsNull() {
        Map<String, Object> result = service.buildHydroDataMap("FR", null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void buildHydroDataMap_returnsEmptyMapWhenHydroPropsByAreaIsEmpty() {
        Map<String, Object> result = service.buildHydroDataMap("FR", Collections.emptyMap());

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "NON_EXISTENT"})
    void buildHydroDataMap_returnsEmptyMapForInvalidArea(String areaName) {
        HydroGenerationDTO dto = HydroGenerationDTO.builder().build();
        Map<String, List<HydroGenerationDTO>> hydroProps = Map.of("FR", List.of(dto));

        Map<String, Object> result = service.buildHydroDataMap(areaName, hydroProps);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void buildHydroDataMap_returnsEmptyMapWhenAreaListIsEmpty() {
        Map<String, List<HydroGenerationDTO>> hydroProps = Map.of("FR", Collections.emptyList());

        Map<String, Object> result = service.buildHydroDataMap("FR", hydroProps);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void buildHydroDataMap_returnsPropertiesMapForValidArea() {
        String[] series = {"file1.arrow", "file2.arrow"};
        Map<String, Double> allocation = Map.of("BE", 0.5, "DE", 0.5);
        HydroGenerationDTO dto = HydroGenerationDTO.builder()
                .followLoadModulation(true)
                .interDailyBreakdown(1)
                .series(series)
                .allocation(allocation)
                .build();
        Map<String, List<HydroGenerationDTO>> hydroProps = Map.of("FR", List.of(dto));

        Map<String, Object> result = service.buildHydroDataMap("FR", hydroProps);

        assertNotNull(result);
        assertEquals(4, result.size());
        assertTrue(result.containsKey("properties"));
        assertTrue(result.containsKey("series"));
        assertTrue(result.containsKey("allocation"));
        assertTrue(result.containsKey("psp"));

        @SuppressWarnings("unchecked")
        List<HydroGenerationDTO> properties = (List<HydroGenerationDTO>) result.get("properties");
        assertEquals(1, properties.size());
        assertNull(properties.get(0).getSeries());
        assertNull(properties.get(0).getAllocation());
        assertEquals(dto.getFollowLoadModulation(), properties.get(0).getFollowLoadModulation());

        assertArrayEquals(series, (String[]) result.get("series"));
        assertEquals(allocation, result.get("allocation"));
    }

    @Test
    void buildHydroDataMap_areaLookupUsesUppercase() {
        HydroGenerationDTO dto = HydroGenerationDTO.builder().followLoadModulation(false).build();
        Map<String, List<HydroGenerationDTO>> hydroProps = Map.of("FR", List.of(dto));

        Map<String, Object> result = service.buildHydroDataMap("fr", hydroProps);

        assertNotNull(result);
        assertTrue(result.containsKey("properties"));
        assertTrue(result.containsKey("series"));
        assertTrue(result.containsKey("allocation"));
        assertArrayEquals(new String[0], (String[]) result.get("series"));
        assertEquals(Collections.emptyMap(), result.get("allocation"));
    }

    @Test
    void buildHydroDataMap_multipleDtosAreAllReturned() {
        String[] series = {"file.arrow"};
        Map<String, Double> allocation = Map.of("BE", 1.0);
        HydroGenerationDTO dto1 = HydroGenerationDTO.builder().interDailyBreakdown(1).series(series).allocation(allocation).build();
        HydroGenerationDTO dto2 = HydroGenerationDTO.builder().interDailyBreakdown(2).series(series).allocation(allocation).build();
        HydroGenerationDTO dto3 = HydroGenerationDTO.builder().interDailyBreakdown(3).series(series).allocation(allocation).build();
        List<HydroGenerationDTO> dtos = List.of(dto1, dto2, dto3);
        Map<String, List<HydroGenerationDTO>> hydroProps = Map.of("FR", dtos);

        Map<String, Object> result = service.buildHydroDataMap("FR", hydroProps);

        @SuppressWarnings("unchecked")
        List<HydroGenerationDTO> returnedDtos = (List<HydroGenerationDTO>) result.get("properties");
        assertEquals(3, returnedDtos.size());
        for (HydroGenerationDTO dto : returnedDtos) {
            assertNull(dto.getSeries());
            assertNull(dto.getAllocation());
        }
        assertArrayEquals(series, (String[]) result.get("series"));
        assertEquals(allocation, result.get("allocation"));
    }

    @Test
    void buildHydroDataMap_mixedCaseAreaNameIsNormalized() {
        HydroGenerationDTO dto = HydroGenerationDTO.builder().build();
        Map<String, List<HydroGenerationDTO>> hydroProps = Map.of("DE", List.of(dto));

        Map<String, Object> result = service.buildHydroDataMap("De", hydroProps);

        assertTrue(result.containsKey("properties"));
    }

    @Test
    void buildHydroDataMap_multipleAreasInMapReturnsOnlyRequestedArea() {
        HydroGenerationDTO dtoFR = HydroGenerationDTO.builder().interDailyBreakdown(1).build();
        HydroGenerationDTO dtoBE = HydroGenerationDTO.builder().interDailyBreakdown(2).build();
        Map<String, List<HydroGenerationDTO>> hydroProps = Map.of(
                "FR", List.of(dtoFR),
                "BE", List.of(dtoBE)
        );

        Map<String, Object> result = service.buildHydroDataMap("BE", hydroProps);

        @SuppressWarnings("unchecked")
        List<HydroGenerationDTO> returnedDtos = (List<HydroGenerationDTO>) result.get("properties");
        assertEquals(1, returnedDtos.size());
        assertEquals(dtoBE.getInterDailyBreakdown(), returnedDtos.get(0).getInterDailyBreakdown());
        assertArrayEquals(new String[0], (String[]) result.get("series"));
        assertEquals(Collections.emptyMap(), result.get("allocation"));
    }

    @Test
    void buildHydroDataMap_seriesIsNullInDto_returnsEmptyArray() {
        HydroGenerationDTO dto = HydroGenerationDTO.builder().series(null).build();
        Map<String, List<HydroGenerationDTO>> hydroProps = Map.of("FR", List.of(dto));

        Map<String, Object> result = service.buildHydroDataMap("FR", hydroProps);

        assertArrayEquals(new String[0], (String[]) result.get("series"));
    }

    @Test
    void buildHydroDataMap_seriesIsEmptyInDto_returnsEmptyArray() {
        HydroGenerationDTO dto = HydroGenerationDTO.builder().series(new String[0]).build();
        Map<String, List<HydroGenerationDTO>> hydroProps = Map.of("FR", List.of(dto));

        Map<String, Object> result = service.buildHydroDataMap("FR", hydroProps);

        assertArrayEquals(new String[0], (String[]) result.get("series"));
    }

    @Test
    void buildHydroDataMap_allocationIsNullInDto_returnsEmptyMap() {
        HydroGenerationDTO dto = HydroGenerationDTO.builder().allocation(null).build();
        Map<String, List<HydroGenerationDTO>> hydroProps = Map.of("FR", List.of(dto));

        Map<String, Object> result = service.buildHydroDataMap("FR", hydroProps);

        assertEquals(Collections.emptyMap(), result.get("allocation"));
    }

    @Test
    void buildHydroDataMap_metadataInSecondDto_isExtracted() {
        String[] series = {"s1"};
        Map<String, Double> allocation = Map.of("A", 1.0);
        HydroGenerationDTO dto1 = HydroGenerationDTO.builder().build();
        HydroGenerationDTO dto2 = HydroGenerationDTO.builder()
                .series(series)
                .allocation(allocation)
                .build();
        Map<String, List<HydroGenerationDTO>> hydroProps = Map.of("FR", List.of(dto1, dto2));

        Map<String, Object> result = service.buildHydroDataMap("FR", hydroProps);

        assertArrayEquals(series, (String[]) result.get("series"));
        assertEquals(allocation, result.get("allocation"));
    }

    @Test
    void buildHydroDataMap_allocationIsEmptyInDto_returnsEmptyMap() {
        HydroGenerationDTO dto = HydroGenerationDTO.builder().allocation(Collections.emptyMap()).build();
        Map<String, List<HydroGenerationDTO>> hydroProps = Map.of("FR", List.of(dto));

        Map<String, Object> result = service.buildHydroDataMap("FR", hydroProps);

        assertEquals(Collections.emptyMap(), result.get("allocation"));
    }
}
