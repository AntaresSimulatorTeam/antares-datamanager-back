package com.rte_france.antares.datamanager_back.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StsGenerationDTO {

    @JsonProperty("enabled")
    private Boolean enabled;

    @JsonProperty("group")
    private String group;

    @JsonProperty("injection_nominal_capacity")
    private Integer injection;

    @JsonProperty("withdrawal_nominal_capacity")
    private Double withdrawal;

    @JsonProperty("reservoir_capacity")
    private Double storage;

    @JsonProperty("efficiency")
    private Double efficiencyInjection;

    @JsonProperty("efficiency_withdrawal")
    private Double efficiencyWithdrawal;

    @JsonProperty("initial_level")
    private Double initialLevel;

    @JsonProperty("initial_level_optim")
    private Boolean initialLevelOptim;
}
