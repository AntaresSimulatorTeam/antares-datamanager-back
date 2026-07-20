package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.HydroAreaGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.HydroGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.HydroPropertiesGenerationDTO;
import com.rte_france.antares.datamanager_back.service.study.impl.HydroToJsonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Collections;
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
        Map<String, HydroAreaGenerationDTO> hydroProps = Map.of("FR", new HydroAreaGenerationDTO(dto, null));

        Map<String, Object> result = service.buildHydroDataMap(areaName, hydroProps);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void buildHydroDataMap_returnsEmptyMapWhenBothHydroAndPspAreNull() {
        Map<String, HydroAreaGenerationDTO> hydroProps = Map.of("FR", new HydroAreaGenerationDTO(null, null));

        Map<String, Object> result = service.buildHydroDataMap("FR", hydroProps);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void buildHydroDataMap_returnsPropertiesMapForHydroOnlyArea() {
        String[] series = {"file1.arrow", "file2.arrow"};
        Map<String, Double> allocation = Map.of("BE", 0.5, "DE", 0.5);
        HydroPropertiesGenerationDTO properties = HydroPropertiesGenerationDTO.builder()
                .followLoadModulation(true)
                .interDailyBreakdown(new BigDecimal("1"))
                .build();
        HydroGenerationDTO hydroDto = HydroGenerationDTO.builder()
                .properties(properties)
                .series(series)
                .allocation(allocation)
                .build();
        Map<String, HydroAreaGenerationDTO> hydroProps = Map.of("FR", new HydroAreaGenerationDTO(hydroDto, null));

        Map<String, Object> result = service.buildHydroDataMap("FR", hydroProps);

        assertNotNull(result);
        assertEquals(4, result.size());
        assertTrue(result.containsKey("properties"));
        assertTrue(result.containsKey("series"));
        assertTrue(result.containsKey("allocation"));
        assertTrue(result.containsKey("psp"));

        HydroPropertiesGenerationDTO returnedProps = (HydroPropertiesGenerationDTO) result.get("properties");
        assertEquals(true, returnedProps.getFollowLoadModulation());
        assertArrayEquals(series, (String[]) result.get("series"));
        assertEquals(allocation, result.get("allocation"));

        @SuppressWarnings("unchecked")
        Map<String, Object> pspMap = (Map<String, Object>) result.get("psp");
        assertTrue(pspMap.isEmpty());
    }

    @Test
    void buildHydroDataMap_pspOnlyArea_hasEmptyHydroFieldsAndPopulatedPsp() {
        String[] pspSeries = {"AT_psp_mingen.arrow"};
        Map<String, Double> pspAllocation = Map.of("FR", 1.0);
        HydroPropertiesGenerationDTO pspProperties = HydroPropertiesGenerationDTO.builder()
                .followLoadModulation(false)
                .build();
        HydroGenerationDTO pspDto = HydroGenerationDTO.builder()
                .properties(pspProperties)
                .series(pspSeries)
                .allocation(pspAllocation)
                .build();
        Map<String, HydroAreaGenerationDTO> hydroProps = Map.of("AT", new HydroAreaGenerationDTO(null, pspDto));

        Map<String, Object> result = service.buildHydroDataMap("AT", hydroProps);

        assertNotNull(result);
        assertNull(result.get("properties"));
        assertArrayEquals(new String[0], (String[]) result.get("series"));
        assertEquals(Collections.emptyMap(), result.get("allocation"));

        @SuppressWarnings("unchecked")
        Map<String, Object> pspMap = (Map<String, Object>) result.get("psp");
        assertFalse(pspMap.isEmpty());
        assertEquals(pspProperties, pspMap.get("properties"));
        assertArrayEquals(pspSeries, (String[]) pspMap.get("series"));
        assertEquals(pspAllocation, pspMap.get("allocation"));
    }

    @Test
    void buildHydroDataMap_bothHydroAndPsp_areBothPresent() {
        HydroGenerationDTO hydroDto = HydroGenerationDTO.builder()
                .properties(HydroPropertiesGenerationDTO.builder().followLoadModulation(true).build())
                .series(new String[]{"AT_mingen.arrow"})
                .allocation(Map.of("FR", 0.5))
                .build();
        HydroGenerationDTO pspDto = HydroGenerationDTO.builder()
                .properties(HydroPropertiesGenerationDTO.builder().followLoadModulation(false).build())
                .series(new String[]{"AT_psp_mingen.arrow"})
                .allocation(Map.of("FR", 1.0))
                .build();
        Map<String, HydroAreaGenerationDTO> hydroProps = Map.of("AT", new HydroAreaGenerationDTO(hydroDto, pspDto));

        Map<String, Object> result = service.buildHydroDataMap("AT", hydroProps);

        assertArrayEquals(new String[]{"AT_mingen.arrow"}, (String[]) result.get("series"));
        assertEquals(true, ((HydroPropertiesGenerationDTO) result.get("properties")).getFollowLoadModulation());

        @SuppressWarnings("unchecked")
        Map<String, Object> pspMap = (Map<String, Object>) result.get("psp");
        assertFalse(pspMap.isEmpty());
        assertArrayEquals(new String[]{"AT_psp_mingen.arrow"}, (String[]) pspMap.get("series"));
        assertEquals(false, ((HydroPropertiesGenerationDTO) pspMap.get("properties")).getFollowLoadModulation());
    }

    @Test
    void buildHydroDataMap_areaLookupUsesUppercase() {
        HydroGenerationDTO dto = HydroGenerationDTO.builder()
                .properties(HydroPropertiesGenerationDTO.builder().followLoadModulation(false).build())
                .build();
        Map<String, HydroAreaGenerationDTO> hydroProps = Map.of("FR", new HydroAreaGenerationDTO(dto, null));

        Map<String, Object> result = service.buildHydroDataMap("fr", hydroProps);

        assertNotNull(result);
        assertTrue(result.containsKey("properties"));
        assertArrayEquals(new String[0], (String[]) result.get("series"));
        assertEquals(Collections.emptyMap(), result.get("allocation"));
    }

    @Test
    void buildHydroDataMap_multipleAreasInMapReturnsOnlyRequestedArea() {
        HydroGenerationDTO dtoFR = HydroGenerationDTO.builder()
                .properties(HydroPropertiesGenerationDTO.builder().interDailyBreakdown(new BigDecimal("1")).build())
                .build();
        HydroGenerationDTO dtoBE = HydroGenerationDTO.builder()
                .properties(HydroPropertiesGenerationDTO.builder().interDailyBreakdown(new BigDecimal("2")).build())
                .build();
        Map<String, HydroAreaGenerationDTO> hydroProps = Map.of(
                "FR", new HydroAreaGenerationDTO(dtoFR, null),
                "BE", new HydroAreaGenerationDTO(dtoBE, null)
        );

        Map<String, Object> result = service.buildHydroDataMap("BE", hydroProps);

        HydroPropertiesGenerationDTO returnedProps = (HydroPropertiesGenerationDTO) result.get("properties");
        assertEquals(new BigDecimal("2"), returnedProps.getInterDailyBreakdown());
        assertArrayEquals(new String[0], (String[]) result.get("series"));
        assertEquals(Collections.emptyMap(), result.get("allocation"));
    }

    @Test
    void buildHydroDataMap_seriesIsNullInDto_returnsEmptyArray() {
        HydroGenerationDTO dto = HydroGenerationDTO.builder().series(null).build();
        Map<String, HydroAreaGenerationDTO> hydroProps = Map.of("FR", new HydroAreaGenerationDTO(dto, null));

        Map<String, Object> result = service.buildHydroDataMap("FR", hydroProps);

        assertArrayEquals(new String[0], (String[]) result.get("series"));
    }

    @Test
    void buildHydroDataMap_seriesIsEmptyInDto_returnsEmptyArray() {
        HydroGenerationDTO dto = HydroGenerationDTO.builder().series(new String[0]).build();
        Map<String, HydroAreaGenerationDTO> hydroProps = Map.of("FR", new HydroAreaGenerationDTO(dto, null));

        Map<String, Object> result = service.buildHydroDataMap("FR", hydroProps);

        assertArrayEquals(new String[0], (String[]) result.get("series"));
    }

    @Test
    void buildHydroDataMap_allocationIsNullInDto_returnsEmptyMap() {
        HydroGenerationDTO dto = HydroGenerationDTO.builder().allocation(null).build();
        Map<String, HydroAreaGenerationDTO> hydroProps = Map.of("FR", new HydroAreaGenerationDTO(dto, null));

        Map<String, Object> result = service.buildHydroDataMap("FR", hydroProps);

        assertEquals(Collections.emptyMap(), result.get("allocation"));
    }

    @Test
    void buildHydroDataMap_allocationIsEmptyInDto_returnsEmptyMap() {
        HydroGenerationDTO dto = HydroGenerationDTO.builder().allocation(Collections.emptyMap()).build();
        Map<String, HydroAreaGenerationDTO> hydroProps = Map.of("FR", new HydroAreaGenerationDTO(dto, null));

        Map<String, Object> result = service.buildHydroDataMap("FR", hydroProps);

        assertEquals(Collections.emptyMap(), result.get("allocation"));
    }
}