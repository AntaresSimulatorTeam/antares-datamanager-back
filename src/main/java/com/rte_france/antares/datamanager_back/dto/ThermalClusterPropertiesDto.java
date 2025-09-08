package com.rte_france.antares.datamanager_back.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
  private Boolean enabled;
  private Integer unitCount;
  private Double nominalCapacity;
  private String group;
  private String genTs;
  private Double minStablePower;
  private Integer minUpTime;
  private Integer minDownTime;
  private Boolean mustRun;
  private Double spinning;
  private Double volatilityForced;
  private Double volatilityPlanned;
  private String lawForced;
  private String lawPlanned;
  private Double marginalCost;
  private Double spreadCost;
  private Double fixedCost;
  private Double startupCost;
  private Double marketBidCost;
  private Double co2;
  private Double nh3;
  private Double so2;
  private Double nox;
  private Double pm2_5;
  private Double pm5;
  private Double pm10;
  private Double nmvoc;
  private Double op1;
  private Double op2;
  private Double op3;
  private Double op4;
  private Double op5;
  private String costGeneration;
  private Double efficiency;
  private Double variableOMCost;
}
