package com.rte_france.antares.datamanager_back.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DsrGenerationDTO {

    @JsonProperty("enabled")
    private Boolean enabled;

    @JsonProperty("group")
    private String group;

    @JsonProperty("nominal_capacity")
    private Double nominalCapacity;

    @JsonProperty("unit_count")
    private Integer unitCount;

    @JsonProperty("marginal_cost")
    private Double marginalCost;

    @JsonProperty("market_bid_cost")
    private Double marketBidCost;

    @JsonProperty("series")
    private List<String> dsrTsList;
}
