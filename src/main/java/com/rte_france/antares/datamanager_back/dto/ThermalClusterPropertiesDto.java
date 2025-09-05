package com.rte_france.antares.datamanager_back.dto;

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
public class ThermalClusterPropertiesDto {
  private boolean enabled;
  private int unitCount;
  private double nominalCapacity;
  private String group;
  private String genTs;
  private double minStablePower;
  private int minUpTime;
  private int minDownTime;
  private boolean mustRun;
  private double spinning;
  private double volatilityForced;
  private double volatilityPlanned;
  private String lawForced;
  private String lawPlanned;
  private double marginalCost;
  private double spreadCost;
  private double fixedCost;
  private double startupCost;
  private double marketBidCost;
  private double co2;
  private double nh3;
  private double so2;
  private double nox;
  private double pm2_5;
  private double pm5;
  private double pm10;
  private double nmvoc;
  private double op1;
  private double op2;
  private double op3;
  private double op4;
  private double op5;
  private String costGeneration;
  private double efficiency;
  private double variableOMCost;

  public static ThermalClusterPropertiesDto defaults() {
    return ThermalClusterPropertiesDto.builder()
            .enabled(true)
            .nominalCapacity(0)
            .unitCount(1)
            .group("OTHER1")
            .genTs("use global")
            .minStablePower(0)
            .minUpTime(1)
            .minDownTime(1)
            .mustRun(false)
            .spinning(0)
            .volatilityForced(0)
            .volatilityPlanned(0)
            .lawForced("uniform")
            .lawPlanned("uniform")
            .marginalCost(0)
            .spreadCost(0)
            .fixedCost(0)
            .startupCost(0)
            .marketBidCost(0)
            .co2(0)
            .nh3(0)
            .so2(0)
            .nox(0)
            .pm2_5(0)
            .pm5(0)
            .pm10(0)
            .nmvoc(0)
            .op1(0)
            .op2(0)
            .op3(0)
            .op4(0)
            .op5(0)
            .costGeneration("set_manually")
            .efficiency(100)
            .variableOMCost(0)
            .build();
  }
}
