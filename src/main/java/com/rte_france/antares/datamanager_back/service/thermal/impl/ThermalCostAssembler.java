package com.rte_france.antares.datamanager_back.service.thermal.impl;

import com.rte_france.antares.datamanager_back.dto.ThermalClusterGenerationDto;
import com.rte_france.antares.datamanager_back.repository.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ThermalCostAssembler {

    private static final Double COFF_GJ_TO_MWH = 3.6;

    private Double round(Double value) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }

    public void computeCo2(ThermalClusterGenerationDto dto, ThermalCommonParameterEntity thermalCommonParameterEntity, String fuel, List<TrajectoryEntity> commonTrajectories, Double ratioNcvHcv) {
        if (thermalCommonParameterEntity != null && thermalCommonParameterEntity.getCo2() != null && thermalCommonParameterEntity.getCo2() != 0.0) {
            dto.setCo2(round(thermalCommonParameterEntity.getCo2() * COFF_GJ_TO_MWH/1000));
        } else if (fuel != null) {
            computeFallbackCo2(dto, fuel, commonTrajectories, ratioNcvHcv);
        }
    }

    private void computeFallbackCo2(ThermalClusterGenerationDto dto, String fuel, List<TrajectoryEntity> commonTrajectories, Double ratioNcvHcv) {
        Double efficiency = dto.getEfficiency();
        if (efficiency == null || efficiency == 0.0) return;

        for (TrajectoryEntity trajectory : commonTrajectories) {
            Integer horizonYear = parseHorizonYear(trajectory.getHorizon());
            if (horizonYear == null) continue;

            findEconomicCo2(trajectory, fuel, horizonYear).ifPresent(economicCo2 -> {
                BigDecimal co2EmissionFuel = economicCo2.getCo2EmissionFuel();
                if (co2EmissionFuel != null && ratioNcvHcv != null && ratioNcvHcv != 0.0) {
                    // Formula: (co2EmissionFuel / 1000) / (efficiency / 100) / ratioNcvHcv
                    dto.setCo2(round((co2EmissionFuel.doubleValue() / 1000.0) / (efficiency / 100.0) / ratioNcvHcv));
                }
            });

            if (dto.getCo2() != null) return;
        }
    }

    private Integer parseHorizonYear(String horizon) {
        if (horizon == null) return null;
        try {
            String value = horizon.trim();
            if (value.contains("-")) {
                String[] parts = value.split("-");
                value = parts[parts.length - 1]; // take year2
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Optional<ThermalEconomicCo2Entity> findEconomicCo2(TrajectoryEntity trajectory, String fuel, Integer horizonYear) {
        return Optional.ofNullable(trajectory.getThermalEconomicCo2s())
                .orElseGet(List::of)
                .stream()
                .filter(e -> e.getFuel() != null &&
                        fuel != null &&
                        e.getFuel().equalsIgnoreCase(fuel) &&
                        horizonYear.equals(e.getYear())
                )
                .findFirst();
    }

    public void computeStartupCost(
            ThermalClusterGenerationDto dto,
            ThermalCommonParameterEntity commonParam,
            String fuel,
            List<ThermalSpecificParametersEntity> specificParams,
            List<ThermalClusterCapacityEntity> capacities,
            List<TrajectoryEntity> trajectories
    ) {
        if (fuel == null) return;

        Double efficiency = dto.getEfficiency();
        if (efficiency == null || efficiency == 0.0) return;

        Double startupFuelCap = getStartupFuelCapacity(capacities, fuel);
        Double enerValue = getEnergyValue(trajectories);
        Double marginalCostValue = getMarginalCost(specificParams, trajectories, fuel, commonParam, dto);

        if (marginalCostValue != null) {
            marginalCostValue = round(marginalCostValue);
            Double startupFixCost = (commonParam != null && commonParam.getStartUpFixCost() != null) ? commonParam.getStartUpFixCost() : 0.0;
            // Formula: startup_fuel * ener_value * efficiency * marginal_cost + startup_fix_cost
            dto.setStartupCost(round((startupFuelCap * enerValue * (efficiency / 100.0) * marginalCostValue) + startupFixCost));
        }

        // Always set the calculated marginal cost back to the DTO if it wasn't there
        if (dto.getMarginalCost() == null) {
            dto.setMarginalCost(marginalCostValue);
        }
    }

    private Double getStartupFuelCapacity(List<ThermalClusterCapacityEntity> capacities, String fuel) {
        return capacities.stream()
                .filter(c -> fuel.equalsIgnoreCase(c.getFuel()))
                .map(ThermalClusterCapacityEntity::getValue)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(0.0);
    }

    private Double getEnergyValue(List<TrajectoryEntity> trajectories) {
        return trajectories.stream()
                .flatMap(t -> Optional.ofNullable(t.getThermalEconomicEnerContents()).orElse(List.of()).stream())
                .map(ThermalEconomicEnerContentEntity::getValue)
                .filter(Objects::nonNull)
                .map(BigDecimal::doubleValue)
                .findFirst()
                .orElse(0.0);
    }

    private Double getMarginalCost(
            List<ThermalSpecificParametersEntity> specificParams,
            List<TrajectoryEntity> trajectories,
            String fuel,
            ThermalCommonParameterEntity commonParam,
            ThermalClusterGenerationDto dto
    ) {
        return specificParams.stream()
                .map(ThermalSpecificParametersEntity::getMarginalCost)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(() -> computeFallbackMarginalCost(trajectories, fuel, commonParam, dto));
    }

    private Double computeFallbackMarginalCost(
            List<TrajectoryEntity> trajectories,
            String fuel,
            ThermalCommonParameterEntity commonParam,
            ThermalClusterGenerationDto dto
    ) {
        Double fuelCost = findFuelCost(trajectories, fuel);
        Double co2Cost = findCo2Cost(trajectories);
        Double efficiency = dto.getEfficiency();

        if (fuelCost != null && co2Cost != null && efficiency != null && efficiency != 0.0) {
            Double omCost = (commonParam != null && commonParam.getOmCost() != null) ? commonParam.getOmCost() : 0.0;
            Double co2Value = dto.getCo2() != null ? dto.getCo2() : 0.0;
            // Formula: fuel / efficiency + CO2 cost * CO2 (calculated in computeCo2) + om_cost
            return (fuelCost / (efficiency / 100.0)) + (co2Cost * co2Value) + omCost;
        }
        return null;
    }

    public void computeMarketBidCost(ThermalClusterGenerationDto dto, ThermalCommonParameterEntity commonParam, List<ThermalSpecificParametersEntity> specificParams) {
        // 1. market_bid de la table thermal_specific_parameters
        Double marketBid = specificParams.stream()
                .map(ThermalSpecificParametersEntity::getMarketBid)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        if (marketBid != null) {
            dto.setMarketBidCost(round(marketBid));
        } else if (dto.getMarginalCost() != null) {
            // 2. marginal-cost already calculated - om_cost from thermal_common_parameters
            Double omCost = (commonParam != null && commonParam.getOmCost() != null) ? commonParam.getOmCost() : 0.0;
            dto.setMarketBidCost(round(dto.getMarginalCost() - omCost));
        }
    }

    private Double findFuelCost(List<TrajectoryEntity> trajectories, String fuel) {
        for (TrajectoryEntity trajectory : trajectories) {
            if (trajectory.getThermalCosts() == null) continue;
            for (ThermalCostEntity costEntity : trajectory.getThermalCosts()) {
                ThermalCostTypeEntity type = costEntity.getThermalType();
                if (type != null && type.getFuel() != null && type.getFuel().equalsIgnoreCase(fuel) && costEntity.getCost() != null) {

                            return costEntity.getCost();

                }
            }
        }
        return null;
    }

    private Double findCo2Cost(List<TrajectoryEntity> trajectories) {
        for (TrajectoryEntity trajectory : trajectories) {
            if (trajectory.getThermalCosts() == null) continue;
            for (ThermalCostEntity costEntity : trajectory.getThermalCosts()) {
                ThermalCostTypeEntity type = costEntity.getThermalType();
                if (type != null && "CO2".equalsIgnoreCase(type.getFuel()) && costEntity.getCost() != null) {

                            return costEntity.getCost();

                }
            }
        }
        return null;
    }
}
