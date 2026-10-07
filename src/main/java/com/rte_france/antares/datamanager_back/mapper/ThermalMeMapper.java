package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.dto.ThermalMEClusterGenerationDto;
import com.rte_france.antares.datamanager_back.repository.model.ThermalMeEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Optional;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ThermalMeMapper {

    private static final String TIMESTEP_ANNUAL = "annual";

    public static ThermalMEClusterGenerationDto mapToThermalMeGenerationDTO(ThermalMeEntity entity) {
        boolean isMarginalCostAnnual = TIMESTEP_ANNUAL.equalsIgnoreCase(entity.getMarginalCostTimestep());
        boolean isMarketBidCostAnnual = TIMESTEP_ANNUAL.equalsIgnoreCase(entity.getMarketBidCostTimestep());
        boolean isCapacityAnnual = TIMESTEP_ANNUAL.equalsIgnoreCase(entity.getCmTimestep());
        boolean isMinGenAnnual = TIMESTEP_ANNUAL.equalsIgnoreCase(entity.getMrTimestep());
        
        return ThermalMEClusterGenerationDto.builder()
                .enabled(Optional.ofNullable(entity.getEnabled()).orElse(false))
                .group(entity.getGroupName())
                .mustRun(Optional.ofNullable(entity.getMustRun()).orElse(false))
                .unitCount(Optional.ofNullable(entity.getNbUnit()).map(Number::intValue).orElse(0))
                .nominalCapacity(Optional.ofNullable(entity.getNominalCapacity()).map(Number::doubleValue).orElse(0.0))
                .marginalCost(Optional.ofNullable(entity.getMarginalCost()).map(Number::doubleValue).orElse(0.0))
                .marketBidCost(Optional.ofNullable(entity.getMarketBidCost()).map(Number::doubleValue).orElse(0.0))
                .marginalCostModulation(isMarginalCostAnnual ? Optional.ofNullable(entity.getMarginalCostModulation()).map(Number::intValue).orElse(null) : null)
                .marketBidModulation(isMarketBidCostAnnual ? Optional.ofNullable(entity.getMarketBidCostModulation()).map(Number::intValue).orElse(null) : null)
                .capacityModulation(isCapacityAnnual ? Optional.ofNullable(entity.getCmModulation()).map(Number::intValue).orElse(null) : null)
                .mustRunModulation(isMinGenAnnual ? Optional.ofNullable(entity.getMrModulation()).map(Number::intValue).orElse(null) : null)
                .build();
    }
}