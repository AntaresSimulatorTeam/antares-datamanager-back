package com.rte_france.antares.datamanager_back.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class HydroPropertiesGenerationDTO {

    @JsonProperty("follow_load")
    private Boolean followLoadModulation;

    @JsonProperty("inter_daily_breakdown")
    private Integer interDailyBreakdown;

    @JsonProperty("inter_daily_modulation")
    private Integer interDailyModulation;

    @JsonProperty("inter_monthly_breakdown")
    private Integer interMonthlyBreakdown;

    @JsonProperty("reservoir")
    private Boolean reservoirManagement;

    @JsonProperty("reservoir_capacity")
    private Integer reservoirCapacity;

    @JsonProperty("pumping_efficiency")
    private Integer pumpingEfficiency;

    @JsonProperty("initialize_reservoir_date")
    private Integer initializeReservoirDate;

    @JsonProperty("use_water")
    private Boolean useWater;
}
