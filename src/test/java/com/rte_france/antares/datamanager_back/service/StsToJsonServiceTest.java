package com.rte_france.antares.datamanager_back.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rte_france.antares.datamanager_back.dto.StsConstraintParameterDTO;
import com.rte_france.antares.datamanager_back.dto.StsGenerationDTO;
import com.rte_france.antares.datamanager_back.service.study.impl.StsToJsonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StsToJsonServiceTest {

    private StsToJsonService stsToJsonService;

    @BeforeEach
    void setUp() {
        stsToJsonService = new StsToJsonService();
    }

    @Test
    void stsMapGenerator_ShouldReturnEmptyMapWhenInputIsNull() {
        Map<String, Object> result = stsToJsonService.stsMapGenerator("FR", null);
        assertTrue(result.isEmpty());
    }

    @Test
    void stsMapGenerator_ShouldReturnEmptyMapWhenInputIsEmpty() {
        Map<String, Object> result = stsToJsonService.stsMapGenerator("FR", Collections.emptyMap());
        assertTrue(result.isEmpty());
    }

    @Test
    void stsMapGenerator_ShouldFilterClustersByAreaPrefix() {
        StsGenerationDTO dto = StsGenerationDTO.builder().enabled(true).injection(100).build();
        Map<String, StsGenerationDTO> input = Map.of(
                "FR_ev_FR", dto,
                "BE_bat", StsGenerationDTO.builder().enabled(false).build()
        );

        Map<String, Object> result = stsToJsonService.stsMapGenerator("FR", input);

        assertEquals(1, result.size());
        assertTrue(result.containsKey("FR_ev_FR"));
        assertFalse(result.containsKey("BE_bat"));
    }

    @Test
    void stsMapGenerator_ShouldIncludePropertiesAndSeries() {
        StsGenerationDTO dto = StsGenerationDTO.builder()
                .enabled(true)
                .groupe("EV")
                .injection(2127)
                .withdrawal(425.0)
                .storage(18052.0)
                .efficiencyInjection(0.95)
                .efficiencyWithdrawal(0.95)
                .initialLevel(0.5)
                .initialLevelOptim(false)
                .stsTsList(List.of("inflows.xlsx.abc.arrow", "lower_curve.xlsx.def.arrow"))
                .stsConstraintsSeriesList(List.of("daily_min.csv.abc.arrow"))
                .constraintParameters(Map.of("daily_min", StsConstraintParameterDTO.builder().variable("injection").build()))
                .build();

        Map<String, Object> result = stsToJsonService.stsMapGenerator("FR", Map.of("FR_ev_FR", dto));

        Map<String, Object> cluster = asMap(result.get("FR_ev_FR"));
        assertNotNull(cluster.get("properties"));
        assertNotNull(cluster.get("series"));

        Map<String, Object> properties = asMap(cluster.get("properties"));
        assertEquals(true, properties.get("enabled"));
        assertEquals("EV", properties.get("group"));
        assertEquals(2127, properties.get("injection_nominal_capacity"));
        assertEquals(425.0, properties.get("withdrawal_nominal_capacity"));
        assertEquals(18052.0, properties.get("reservoir_capacity"));
        assertEquals(0.95, properties.get("efficiency"));
        assertEquals(0.95, properties.get("efficiency_withdrawal"));
        assertEquals(0.5, properties.get("initial_level"));
        assertEquals(false, properties.get("initial_level_optim"));
        assertNull(properties.get("stsConstraintsSeriesList"));
        assertNull(properties.get("constraintParameters"));

        Map<String, Object> constraintParameters = asMap(cluster.get("constraintParameters"));
        assertNotNull(constraintParameters);
        Map<String, Object> dailyMin = asMap(constraintParameters.get("daily_min"));
        assertEquals("injection", dailyMin.get("variable"));

        assertEquals(List.of("daily_min.csv.abc.arrow"), cluster.get("stsConstraintsSeriesList"));
        assertNull(cluster.get("constraints"));

        Map<String, Object> series = asMap(cluster.get("series"));
        assertEquals(List.of("inflows.xlsx.abc.arrow", "lower_curve.xlsx.def.arrow"), series.get("series"));
    }

    @Test
    void stsMapGenerator_ShouldOmitConstraintsKey() {
        StsGenerationDTO dto = StsGenerationDTO.builder()
                .enabled(true)
                .injection(100)
                .build();

        Map<String, Object> result = stsToJsonService.stsMapGenerator("FR", Map.of("FR_S1", dto));

        Map<String, Object> cluster = asMap(result.get("FR_S1"));
        assertFalse(cluster.containsKey("constraints"));
    }

    @Test
    void stsMapGenerator_ConstraintsAreDirectSiblingsOfProperties_WithNoDataWrapping() {
        StsConstraintParameterDTO param = StsConstraintParameterDTO.builder()
                .variable("injection")
                .operator("greater")
                .enabled("true")
                .hours(List.of(List.of(1, 1, 24)))
                .build();

        StsGenerationDTO dto = StsGenerationDTO.builder()
                .enabled(true)
                .groupe("EV")
                .injection(2156)
                .withdrawal(320.0)
                .storage(1569.0)
                .efficiencyInjection(0.89)
                .efficiencyWithdrawal(0.7)
                .initialLevel(0.5)
                .initialLevelOptim(false)
                .stsConstraintsSeriesList(List.of("series1", "series2", "series3"))
                .constraintParameters(Map.of("param1", param))
                .build();

        Map<String, Object> result = stsToJsonService.stsMapGenerator("FR", Map.of("FR_EV_FR", dto));

        Map<String, Object> cluster = asMap(result.get("FR_EV_FR"));

        // constraintParameters and stsConstraintsSeriesList must be direct siblings of properties — no "constraints" wrapper
        assertFalse(cluster.containsKey("constraints"), "must not have a 'constraints' wrapper key");
        assertTrue(cluster.containsKey("constraintParameters"), "constraintParameters must be at cluster level");
        assertTrue(cluster.containsKey("stsConstraintsSeriesList"), "stsConstraintsSeriesList must be at cluster level");

        // constraintParameters param object must not be wrapped in {"data": ...}
        Map<String, Object> constraintParameters = asMap(cluster.get("constraintParameters"));
        Map<String, Object> param1 = asMap(constraintParameters.get("param1"));
        assertNotNull(param1, "param1 must exist");
        assertFalse(param1.containsKey("data"), "param must not be wrapped in a 'data' key");
        assertEquals("injection", param1.get("variable"));
        assertEquals("greater", param1.get("operator"));

        // stsConstraintsSeriesList: plain list, not under "constraints_series"
        assertEquals(List.of("series1", "series2", "series3"), cluster.get("stsConstraintsSeriesList"));
        assertNull(cluster.get("constraints_series"), "old key 'constraints_series' must not appear");
    }

    @Test
    void stsMapGenerator_MatchesTargetJsonStructure() throws JsonProcessingException {
        // Given
        StsConstraintParameterDTO dailyMinV2G = StsConstraintParameterDTO.builder()
                .variable("injection")
                .operator("greater")
                .enabled("true")
                .hours(List.of(List.of(1, 1, 24), List.of(2, 25, 48)))
                .build();

        StsConstraintParameterDTO v2gLimit = StsConstraintParameterDTO.builder()
                .variable("withdrawal")
                .operator("less")
                .enabled("true")
                .hours(List.of(List.of(1, 1, 168)))
                .build();

        StsGenerationDTO dto = StsGenerationDTO.builder()
                .enabled(true)
                .groupe("EV")
                .injection(2127)
                .withdrawal(425.0)
                .storage(18052.0)
                .efficiencyInjection(0.95)
                .efficiencyWithdrawal(0.95)
                .initialLevel(0.5)
                .initialLevelOptim(false)
                .stsTsList(List.of(
                        "inflows.xlsx.ee6bc9e2-5e4e-4ddb-9bd9-e139f1494465.arrow",
                        "lower_curve.xlsx.97d9f8f0-a5d9-48ff-a5a4-663cd5fb1be6.arrow",
                        "Pmax_injection.xlsx.196da505-c346-4f51-a185-2b4db5b3e205.arrow",
                        "Pmax_soutirage.xlsx.68331b9a-3ed1-4a0a-b309-05395385867f.arrow",
                        "upper_curve.xlsx.d93e504a-078d-41ea-af50-a78c3996f9c0.arrow"
                ))
                .stsConstraintsSeriesList(List.of(
                        "daily_min_v2g_fr.csv.f4570c6b-a06c-4938-a82a-e3135549f7de.arrow",
                        "v2g_limit_fr.csv.720c8bbc-eb20-40a4-9dd8-08ea54db519c.arrow"
                ))
                .constraintParameters(new LinkedHashMap<>(Map.of(
                        "daily_min_v2g_fr", dailyMinV2G,
                        "v2g_limit_fr", v2gLimit
                )))
                .build();

        // When
        Map<String, Object> result = stsToJsonService.stsMapGenerator("FR", Map.of("FR_ev_FR", dto));

        // Then
        ObjectMapper mapper = new ObjectMapper();
        String jsonResult = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);

        // Target JSON structure based on requirements
        String expectedJson = """
                {
                  "FR_ev_FR" : {
                    "properties" : {
                      "enabled" : true,
                      "group" : "EV",
                      "injection_nominal_capacity" : 2127,
                      "withdrawal_nominal_capacity" : 425.0,
                      "reservoir_capacity" : 18052.0,
                      "efficiency" : 0.95,
                      "efficiency_withdrawal" : 0.95,
                      "initial_level" : 0.5,
                      "initial_level_optim" : false
                    },
                    "series" : {
                      "series" : [ "inflows.xlsx.ee6bc9e2-5e4e-4ddb-9bd9-e139f1494465.arrow", "lower_curve.xlsx.97d9f8f0-a5d9-48ff-a5a4-663cd5fb1be6.arrow", "Pmax_injection.xlsx.196da505-c346-4f51-a185-2b4db5b3e205.arrow", "Pmax_soutirage.xlsx.68331b9a-3ed1-4a0a-b309-05395385867f.arrow", "upper_curve.xlsx.d93e504a-078d-41ea-af50-a78c3996f9c0.arrow" ]
                    },
                    "constraintParameters" : {
                      "daily_min_v2g_fr" : {
                        "variable" : "injection",
                        "operator" : "greater",
                        "enabled" : "true",
                        "hours" : [ [ 1, 1, 24 ], [ 2, 25, 48 ] ]
                      },
                      "v2g_limit_fr" : {
                        "variable" : "withdrawal",
                        "operator" : "less",
                        "enabled" : "true",
                        "hours" : [ [ 1, 1, 168 ] ]
                      }
                    },
                    "stsConstraintsSeriesList" : [ "daily_min_v2g_fr.csv.f4570c6b-a06c-4938-a82a-e3135549f7de.arrow", "v2g_limit_fr.csv.720c8bbc-eb20-40a4-9dd8-08ea54db519c.arrow" ]
                  }
                }""";

        Map<String, Object> resultMap = mapper.readValue(jsonResult, new TypeReference<>() {});
        Map<String, Object> expectedMap = mapper.readValue(expectedJson, new TypeReference<>() {});

        assertEquals(expectedMap, resultMap);
    }

    @Test
    void stsMapGenerator_ShouldOmitEmptyConstraintCollections() {
        StsGenerationDTO dto = StsGenerationDTO.builder()
                .enabled(true)
                .constraintParameters(Collections.emptyMap())
                .stsConstraintsSeriesList(Collections.emptyList())
                .build();

        Map<String, Object> result = stsToJsonService.stsMapGenerator("FR", Map.of("FR_S1", dto));
        Map<String, Object> cluster = asMap(result.get("FR_S1"));

        assertNotNull(cluster);
        assertFalse(cluster.containsKey("constraintParameters"));
        assertFalse(cluster.containsKey("stsConstraintsSeriesList"));
    }

    @Test
    void stsMapGenerator_ShouldKeepNonEmptySeriesWhenParametersAreEmpty() {
        StsGenerationDTO dto = StsGenerationDTO.builder()
                .enabled(true)
                .constraintParameters(Collections.emptyMap())
                .stsConstraintsSeriesList(List.of("daily_min_fr.csv.arrow"))
                .build();

        Map<String, Object> result = stsToJsonService.stsMapGenerator("FR", Map.of("FR_S2", dto));
        Map<String, Object> cluster = asMap(result.get("FR_S2"));

        assertNotNull(cluster);
        assertFalse(cluster.containsKey("constraintParameters"));
        assertEquals(List.of("daily_min_fr.csv.arrow"), cluster.get("stsConstraintsSeriesList"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        assertInstanceOf(Map.class, value);
        return (Map<String, Object>) value;
    }
}
