package com.rte_france.antares.datamanager_back.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record P2gClusterGenerationDTO(
        P2gPropertiesGenerationDTO properties,
        String modulation,
        Map<String, P2gClusterGenerationDTO.Link> links,
        P2gClusterGenerationDTO.AsserviParameters parameters
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Link(Double capacity, @JsonProperty("fatal_band") Double fatalBand) {}

    public record AsserviParameters(
            @JsonProperty("FC_electrolyseur") Double fcElectrolyseur,
            @JsonProperty("Facteur_surdimension_ENR") Double facteurSurdimensionEnr,
            @JsonProperty("Part_PV_mix") Double partPvMix
    ) {}
}
