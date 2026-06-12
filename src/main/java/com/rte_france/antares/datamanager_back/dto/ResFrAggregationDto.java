package com.rte_france.antares.datamanager_back.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record ResFrAggregationDto(
        @JsonProperty("zone_weights")            Map<String, Double> zoneWeights,
        @JsonProperty("tech_weights_by_zone")    Map<String, Map<String, Double>> techWeightsByZone,
        @JsonProperty("series_by_zone_and_tech") Map<String, Map<String, String>> seriesByZoneAndTech
) {}
