package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.HydroGenerationDTO;
import com.rte_france.antares.datamanager_back.service.study.impl.HydroToJsonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    @Test
    void buildHydroDataMap_returnsEmptyMapWhenAreaNameIsNull() {
        HydroGenerationDTO dto = HydroGenerationDTO.builder().build();
        Map<String, List<HydroGenerationDTO>> hydroProps = Map.of("FR", List.of(dto));

        Map<String, Object> result = service.buildHydroDataMap(null, hydroProps);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void buildHydroDataMap_returnsEmptyMapWhenAreaNameIsBlank() {
        HydroGenerationDTO dto = HydroGenerationDTO.builder().build();
        Map<String, List<HydroGenerationDTO>> hydroProps = Map.of("FR", List.of(dto));

        Map<String, Object> result = service.buildHydroDataMap("   ", hydroProps);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void buildHydroDataMap_returnsEmptyMapWhenAreaNameIsEmpty() {
        HydroGenerationDTO dto = HydroGenerationDTO.builder().build();
        Map<String, List<HydroGenerationDTO>> hydroProps = Map.of("FR", List.of(dto));

        Map<String, Object> result = service.buildHydroDataMap("", hydroProps);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void buildHydroDataMap_returnsEmptyMapWhenAreaNotFoundInProps() {
        HydroGenerationDTO dto = HydroGenerationDTO.builder().build();
        Map<String, List<HydroGenerationDTO>> hydroProps = Map.of("BE", List.of(dto));

        Map<String, Object> result = service.buildHydroDataMap("FR", hydroProps);

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
        HydroGenerationDTO dto = HydroGenerationDTO.builder()
                .followLoadModulation(true)
                .interDailyBreakdown(1)
                .build();
        Map<String, List<HydroGenerationDTO>> hydroProps = Map.of("FR", List.of(dto));

        Map<String, Object> result = service.buildHydroDataMap("FR", hydroProps);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("properties"));
        assertEquals(List.of(dto), result.get("properties"));
    }

    @Test
    void buildHydroDataMap_areaLookupUsesUppercase() {
        HydroGenerationDTO dto = HydroGenerationDTO.builder().followLoadModulation(false).build();
        Map<String, List<HydroGenerationDTO>> hydroProps = Map.of("FR", List.of(dto));

        Map<String, Object> result = service.buildHydroDataMap("fr", hydroProps);

        assertNotNull(result);
        assertTrue(result.containsKey("properties"));
        assertEquals(List.of(dto), result.get("properties"));
    }

    @Test
    void buildHydroDataMap_multipleDtosAreAllReturned() {
        HydroGenerationDTO dto1 = HydroGenerationDTO.builder().interDailyBreakdown(1).build();
        HydroGenerationDTO dto2 = HydroGenerationDTO.builder().interDailyBreakdown(2).build();
        HydroGenerationDTO dto3 = HydroGenerationDTO.builder().interDailyBreakdown(3).build();
        List<HydroGenerationDTO> dtos = List.of(dto1, dto2, dto3);
        Map<String, List<HydroGenerationDTO>> hydroProps = Map.of("FR", dtos);

        Map<String, Object> result = service.buildHydroDataMap("FR", hydroProps);

        @SuppressWarnings("unchecked")
        List<HydroGenerationDTO> returnedDtos = (List<HydroGenerationDTO>) result.get("properties");
        assertEquals(3, returnedDtos.size());
        assertEquals(dtos, returnedDtos);
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
        assertEquals(dtoBE, returnedDtos.get(0));
    }
}
