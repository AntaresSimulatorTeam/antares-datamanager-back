package com.rte_france.antares.datamanager_back.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record P2gPropertiesGenerationDTO(
        @JsonProperty("nominal_capacity") Double nominalCapacity,
        Double cost
) {}
