package com.rte_france.antares.datamanager_back.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class HydroGenerationDTO {

    @JsonProperty("follow_load")
    private HydroPropertiesGenerationDTO properties;

    @JsonProperty("allocation")
    private Map<String, Double> allocation;

    @JsonProperty("series")
    private String[] series;
}
