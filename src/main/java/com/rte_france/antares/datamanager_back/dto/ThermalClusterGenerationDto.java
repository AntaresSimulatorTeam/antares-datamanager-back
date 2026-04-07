package com.rte_france.antares.datamanager_back.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.rte_france.antares.datamanager_back.repository.model.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ThermalClusterGenerationDto {

    public static class ThermalClusterViews {
        public interface Properties {}
        public interface Data {}
        public interface ParamModulation {}
    }

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("enabled")
    private Boolean enabled;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("unit_count")
    private Integer unitCount;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("nominal_capacity")
    private Double nominalCapacity;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("group")
    private String group;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("gen_ts")
    private String genTs;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("min_stable_power")
    private Double minStablePower;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("min_up_time")
    private Integer minUpTime;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("min_down_time")
    private Integer minDownTime;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("must_run")
    private Boolean mustRun;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("spinning")
    private Double spinning;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("volatility_forced")
    private Double volatilityForced;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("volatility_planned")
    private Double volatilityPlanned;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("law_forced")
    private String lawForced;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("law_planned")
    private String lawPlanned;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("marginal_cost")
    private Integer marginalCost;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("spread_cost")
    private Double spreadCost;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("fixed_cost")
    private Double fixedCost;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("startup_cost")
    private Integer startupCost;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("market_bid_cost")
    private Double marketBidCost;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("co2")
    private Double co2;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("nh3")
    private Double nh3;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("so2")
    private Double so2;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("nox")
    private Double nox;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("pm2_5")
    private Double pm2_5;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("pm5")
    private Double pm5;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("pm10")
    private Double pm10 ;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("nmvoc")
    private Double nmvoc;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("op1")
    private Double op1;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("op2")
    private Double op2;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("op3")
    private Double op3;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("op4")
    private Double op4;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("op5")
    private Double op5;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("cost_generation")
    private String costGeneration;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("efficiency")
    private Double efficiency;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("variable_o_m_cost")
    private Double variableOMCost;

    @JsonView(ThermalClusterViews.Data.class)
    @JsonProperty("fo_duration")
    private Double foDuration;

    @JsonView(ThermalClusterViews.Data.class)
    @JsonProperty("po_duration")
    private Double poDuration;

    @JsonView(ThermalClusterViews.Data.class)
    @JsonProperty("npo_max_winter")
    private Double npoMaxWinter;

    @JsonView(ThermalClusterViews.Data.class)
    @JsonProperty("npo_max_summer")
    private Double npoMaxSummer;

    @JsonView(ThermalClusterViews.Data.class)
    @JsonProperty("po_common_rate")
    private Double poCommonRate;

    @JsonView(ThermalClusterViews.Data.class)
    @JsonProperty("po_common_duration")
    private Double poCommonDuration;

    @JsonView(ThermalClusterViews.Data.class)
    @JsonProperty("fo_common_rate")
    private Double foCommonRate;

    @JsonView(ThermalClusterViews.Data.class)
    @JsonProperty("fo_common_duration")
    private Double foCommonDuration;

    @JsonView(ThermalClusterViews.Data.class)
    @JsonProperty("nb_unit")
    private Integer nbUnit;

    @JsonView(ThermalClusterViews.Data.class)
    @JsonProperty("fo_monthly_rate")
    private List<Double> foMonthlyRate;

    @JsonView(ThermalClusterViews.Data.class)
    @JsonProperty("po_monthly_rate")
    private List<Double> poMonthlyRate;

    @JsonView(ThermalClusterViews.ParamModulation.class)
    @JsonProperty("modulation")
    private List<String> paramModulationTsList;

}
