package com.rte_france.antares.datamanager_back.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class AreaDTO {
    @JsonProperty("areaName")
    String name;

    @JsonProperty("areaY")
    String y;

    @JsonProperty("areaX")
    Double x;

    @JsonProperty("areaColorR")
    String r;

    @JsonProperty("areaColorG")
    String g;

    @JsonProperty("areaColorB")
    String b;

    @JsonProperty("energy_cost_unsupplied")
    String unsuppliedEnergyCost;

    @JsonProperty("energy_cost_spilled")
    String spilledEnergyCost;

    @JsonProperty(value = "last_modified_date", access = JsonProperty.Access.WRITE_ONLY)
    LocalDateTime lastModifiedDate;

    @JsonProperty("adequacy_patch_mode")
    String adequacyPatchMode;
}
