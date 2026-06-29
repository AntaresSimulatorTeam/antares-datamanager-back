package com.rte_france.antares.datamanager_back.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class HydroPropertiesGenerationDTO {

    @JsonProperty("follow_load")
    private Boolean followLoadModulation;

    @JsonProperty("inter_daily_breakdown")
    private BigDecimal interDailyBreakdown;

    @JsonProperty("inter_daily_modulation")
    private BigDecimal interDailyModulation;

    @JsonProperty("inter_monthly_breakdown")
    private BigDecimal interMonthlyBreakdown;

    @JsonProperty("reservoir")
    private Boolean reservoirManagement;

    @JsonProperty("reservoir_capacity")
    private BigDecimal reservoirCapacity;

    @JsonProperty("pumping_efficiency")
    private BigDecimal pumpingEfficiency;

    @JsonProperty("initialize_reservoir_date")
    private BigDecimal initializeReservoirDate;

    @JsonProperty("use_water")
    private Boolean useWater;
}
