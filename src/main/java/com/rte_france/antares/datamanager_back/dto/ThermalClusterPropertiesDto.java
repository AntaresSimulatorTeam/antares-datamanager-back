package com.rte_france.antares.datamanager_back.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ThermalClusterPropertiesDto {
  @JsonProperty("enabled")
  private Boolean enabled;

  @JsonProperty("unit_count")
  private Integer unitCount;

  @JsonProperty("nominal_capacity")
  private Double nominalCapacity;

  @JsonProperty("group")
  private String group;

  @JsonProperty("gen_ts")
  private String genTs;

  @JsonProperty("min_stable_power")
  private Double minStablePower;

  @JsonProperty("min_up_time")
  private Integer minUpTime;

  @JsonProperty("min_down_time")
  private Integer minDownTime;

  @JsonProperty("must_run")
  private Boolean mustRun;

  @JsonProperty("spinning")
  private Double spinning;

  @JsonProperty("volatility_forced")
  private Double volatilityForced;

  @JsonProperty("volatility_planned")
  private Double volatilityPlanned;

  @JsonProperty("law_forced")
  private String lawForced;

  @JsonProperty("law_planned")
  private String lawPlanned;

  @JsonProperty("marginal_cost")
  private Double marginalCost;

  @JsonProperty("spread_cost")
  private Double spreadCost;

  @JsonProperty("fixed_cost")
  private Double fixedCost;

  @JsonProperty("startup_cost")
  private Double startupCost;

  @JsonProperty("market_bid_cost")
  private Double marketBidCost;

  @JsonProperty("co2")
  private Double co2;

  @JsonProperty("nh3")
  private Double nh3;

  @JsonProperty("so2")
  private Double so2;

  @JsonProperty("nox")
  private Double nox;

  @JsonProperty("pm2_5")
  private Double pm2_5;

  @JsonProperty("pm5")
  private Double pm5;

  @JsonProperty("pm10")
  private Double pm10;

  @JsonProperty("nmvoc")
  private Double nmvoc;

  @JsonProperty("op1")
  private Double op1;

  @JsonProperty("op2")
  private Double op2;

  @JsonProperty("op3")
  private Double op3;

  @JsonProperty("op4")
  private Double op4;

  @JsonProperty("op5")
  private Double op5;

  @JsonProperty("cost_generation")
  private String costGeneration;

  @JsonProperty("efficiency")
  private Double efficiency;

  @JsonProperty("variable_o_m_cost")
  private Double variableOMCost;
}
