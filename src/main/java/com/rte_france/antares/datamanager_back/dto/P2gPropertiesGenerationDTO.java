package com.rte_france.antares.datamanager_back.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record P2gPropertiesGenerationDTO(
        @JsonProperty("nominal_capacity") Double nominalCapacity,
        Double cost,
        @JsonProperty("adequacy_patch_mode") String adequacyPatchMode,
        @JsonProperty("energy_cost_spilled") String energyCostSpilled,
        @JsonProperty("energy_cost_unsupplied") String energyCostUnsupplied
) {}
