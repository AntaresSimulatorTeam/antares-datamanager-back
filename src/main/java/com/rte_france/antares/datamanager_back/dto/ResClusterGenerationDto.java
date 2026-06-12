package com.rte_france.antares.datamanager_back.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResClusterGenerationDto(
        ResClusterPropertiesDto properties,
        List<String> series,
        @JsonProperty("fr_aggregation") ResFrAggregationDto frAggregation
) {}
