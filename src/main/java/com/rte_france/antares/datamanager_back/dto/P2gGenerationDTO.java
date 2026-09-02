package com.rte_france.antares.datamanager_back.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record P2gGenerationDTO(
        @JsonProperty("market_modulation") String marketModulation,
        P2gClusterGenerationDTO base,
        P2gClusterGenerationDTO marg,
        P2gClusterGenerationDTO methanation,
        P2gClusterGenerationDTO asservi
) {}
