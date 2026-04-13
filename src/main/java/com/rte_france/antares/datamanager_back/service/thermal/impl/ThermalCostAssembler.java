package com.rte_france.antares.datamanager_back.service.thermal.impl;

import com.rte_france.antares.datamanager_back.dto.ThermalClusterGenerationDto;
import com.rte_france.antares.datamanager_back.repository.ThermalCostTypeRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
@Slf4j
@Component
@RequiredArgsConstructor
public class ThermalCostAssembler {

    private final ThermalCostTypeRepository thermalCostTypeRepository;
    private static final Double MWH_TO_GJ = 3.6;
    private static final String CO2="CO2";

    private Double round(Double value, Integer precision) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(precision, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Computes the CO2 emissions for a given thermal cluster generation based on the provided parameters.
     * If a CO2 value is available in the thermal common parameter entity, the method calculates CO2 emissions
     * using the specified efficiency. Otherwise, it uses a fallback computation based on the provided fuel,
     * trajectories, and ratio of net calorific value to gross calorific value.
     *
     * @param dto Object representing the thermal cluster generation data where the computed CO2 emissions will be set.
     * @param thermalCommonParameterEntity List of entities containing common thermal parameters.
     * @param economicTrajectory A trajectory entity that might contain relevant data for fallback calculations.
     */
    public void computeCo2(ThermalClusterGenerationDto dto, List<ThermalCommonParameterEntity> thermalCommonParameterEntity, ThermalEconomicEnerContentEntity economicTrajectory) {
        thermalCommonParameterEntity.forEach(commonParam -> {
            String fuel = commonParam.getFuel();
            Double ratioNcvHcv = null;
            if (fuel != null) {
                ratioNcvHcv = thermalCostTypeRepository.findByFuelIgnoreCase(fuel)
                        .map(ThermalCostTypeEntity::getRatioNcvHcv)
                        .orElse(null);
            }

            if (commonParam.getCo2() != null && commonParam.getCo2() != 0.0) {
                Double efficiency = dto.getEfficiency();
                if (efficiency != null && efficiency > 1.0) {
                    efficiency = efficiency / 100.0;
                }
                // Efficiency as a decimal ratio (e.g., 0.15 for 15%)
                Double efficiencyRatio = efficiency;

                // Unit conversion factor from kg/GJ to t/MWh:
                // 1. Multiply by 3.6 to convert from GJ to MWh (1 MWh = 3.6 GJ)
                // 2. Divide by 1000 to convert from kg to tonnes
                Double kgGjToTMwh = MWH_TO_GJ / 1000.0;

                // Final CO2 emission in t/MWh
                dto.setCo2(round((commonParam.getCo2() * kgGjToTMwh) / efficiencyRatio,2));
            } else if (fuel != null && economicTrajectory != null) {
                computeFallbackCo2(dto, fuel, economicTrajectory, ratioNcvHcv);
            }
        });
    }

    /**
     * Computes the fallback CO2 emissions for a given thermal cluster generation based on the provided parameters.
     * This method calculates CO2 emissions using efficiency and energy content ratios, or falls back to alternative
     * computations based on economic trajectories and CO2 cost if standard parameters are not available.
     * Compute CO2 emissions from an economic emission factor when available,
     * otherwise fallback to CO2 cost from thermal_cost.
     * Formula: (co2 / 1000) / efficiency / NCV_HHV_ratio
     *
     * @param dto Object representing the thermal cluster generation data where the computed CO2 emissions will be set.
     * @param fuel The fuel type associated with the thermal generation, used for retrieving economic CO2 emissions.
     * @param economicTrajectory An entity containing economic energy content and CO2-related data used in fallback calculations.
     * @param ratioNcvHcv Ratio of net calorific value to gross calorific value, influencing the efficiency-adjusted computation.
     */
    private void computeFallbackCo2(ThermalClusterGenerationDto dto, String fuel, ThermalEconomicEnerContentEntity economicTrajectory, Double ratioNcvHcv) {
        Double rawEfficiency = dto.getEfficiency();
        if (rawEfficiency == null || rawEfficiency == 0.0) return;
        final double efficiency = (rawEfficiency > 1.0) ? rawEfficiency / 100.0 : rawEfficiency;

        TrajectoryEntity trajectory = economicTrajectory.getTrajectory();
        Integer horizonYear = parseHorizonYear(trajectory.getHorizon());

        // Computes CO2 emissions using economic data or cost fallback
        findEconomicCo2(trajectory, fuel, horizonYear).ifPresentOrElse(economicCo2 -> {
            BigDecimal co2EmissionFuel = economicCo2.getCo2EmissionFuel();
            if (co2EmissionFuel != null) {
                double rationNcvHcv = (ratioNcvHcv != null && ratioNcvHcv != 0.0) ? ratioNcvHcv : 1.0;
                // Formula: (co2EmissionFuel / 1000) / efficiency / ratioNcvHcv
                dto.setCo2(round((co2EmissionFuel.doubleValue() / 1000.0) / efficiency / rationNcvHcv,2));
            }
        }, () -> {
            Double co2Cost = findCo2Cost(trajectory);
            if (co2Cost != null) {
                double rationNcvHcv = (ratioNcvHcv != null && ratioNcvHcv != 0.0) ? ratioNcvHcv : 1.0;
                // Using CO2 cost from thermal_cost as a fallback if economic co2 is not found
                dto.setCo2(round((co2Cost / 1000.0) / efficiency / rationNcvHcv,2));
            }
        });

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
                        e.getFuel().equalsIgnoreCase(fuel) &&
                        (horizonYear == null || horizonYear.equals(e.getYear()))
                )
                .findFirst();
    }


    /**
     * @param dto Object representing the thermal cluster generation data where the results
     *            of the computed startup and marginal costs will be set.
     * @param commonParams List of entities containing common thermal parameters, including
     *                     information about fuels and thermal clusters.
     * @param specificParams List of entities containing specific thermal parameters used
     *                       for the calculation of marginal costs.
     * @param thermalClusterCapacities List of thermal cluster capacity entities providing
     *                                  information about fuel capacities for different thermal clusters.
     * @param economicCostTrajectory A trajectory entity containing economic cost data that may
     *                                be used for the computation of fallback costs or adjustments.
     *  This method handles the absence of common parameters by defaulting to the fuels specified in the
     *  thermal cluster capacities. It then calculates the startup cost based on the fuel capacity, energy
     *  values, efficiencies, and any fixed startup costs defined in the common parameters.
     *  The final results, including the calculated startup and marginal costs, are set back into the provided
     *   DTO (dto).
     */
    public void computeStartupAndMarginalCost(
            ThermalClusterGenerationDto dto,
            List<ThermalCommonParameterEntity> commonParams,
            List<ThermalSpecificParametersEntity> specificParams,
            List<ThermalClusterCapacityEntity> thermalClusterCapacities,
            TrajectoryEntity economicCostTrajectory
    ) {
        // Retrieve the set of fuels used in the thermal cluster capacities, filtering out null values
        Set<String> capacityFuels = thermalClusterCapacities.stream()
                .map(ThermalClusterCapacityEntity::getFuel)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // If common parameters are empty, initialize the stream with a single null value, otherwise stream them
        Stream<ThermalCommonParameterEntity> paramStream =
                commonParams.isEmpty()
                        ? Stream.of((ThermalCommonParameterEntity) null)
                        : commonParams.stream();
        // Iterate over each common parameter and associated fuel, or over just the available fuels if no common parameters
        paramStream
                .flatMap(commonParam -> {
                    // Determine the set of fuels to iterate over, either from common parameters or from the cluster capacities
                    Set<String> fuels =
                            (commonParam != null && commonParam.getFuel() != null)
                                    ? Set.of(commonParam.getFuel())
                                    : capacityFuels;

                    return fuels.stream()
                            .map(fuel -> new AbstractMap.SimpleEntry<>(commonParam, fuel));
                })
                .forEach(entry ->
                        // For each combination of common parameter and fuel, call the calculation method
                        computeStartupAndMarginalCostForFuel(
                                dto,
                                entry.getKey(),
                                entry.getValue(),
                                specificParams,
                                thermalClusterCapacities,
                                economicCostTrajectory
                        )
                );
    }

    private void computeStartupAndMarginalCostForFuel(
            ThermalClusterGenerationDto dto,
            ThermalCommonParameterEntity commonParam,
            String fuel,
            List<ThermalSpecificParametersEntity> specificParams,
            List<ThermalClusterCapacityEntity> thermalClusterCapacities,
            TrajectoryEntity economicCostTrajectory
    ) {
        ThermalCostEntity thermalCostEntity = findThermalCostForFuel(economicCostTrajectory, fuel);

        Double efficiency = normalizeEfficiency(dto.getEfficiency());
        if (efficiency == null) {
            return;
        }

        int startupFuel = getStartupFuel(commonParam);

        ThermalSpecificParametersEntity specificParam = findMatchingSpecificParam(commonParam, specificParams, thermalClusterCapacities);

        MarginalCostResult result = getMarginalCost(
                specificParam, thermalCostEntity, fuel, commonParam, dto
        );

        Double marginalCost = result.value();
        MarginalCostResult.Source source = result.source();
        log.info("Marginal cost calculation for fuel {} and common param {}: source={}, value={}", fuel, commonParam, source, marginalCost);
        dto.setMarginalCostSource(source);
        dto.setMarginalCost(marginalCost);

        updateStartupCost(dto, commonParam, startupFuel, efficiency, marginalCost);


    }

    private ThermalCostEntity findThermalCostForFuel(TrajectoryEntity economicCostTrajectory, String fuel) {
        return Optional.ofNullable(economicCostTrajectory)
                .flatMap(t -> t.getThermalCosts().stream()
                        .filter(c -> c.getThermalType() != null && fuel.equalsIgnoreCase(c.getThermalType().getFuel()))
                        .findFirst())
                .orElse(null);
    }

    private Double normalizeEfficiency(Double efficiency) {
        if (efficiency == null || efficiency == 0.0) {
            return null;
        }
        return efficiency > 1.0 ? efficiency / 100.0 : efficiency;
    }

    private Integer getStartupFuel(ThermalCommonParameterEntity commonParam) {
        return Math.toIntExact(Math.round((commonParam != null && commonParam.getStartUpFuel() != null) ? commonParam.getStartUpFuel() : 0));
    }

    private ThermalSpecificParametersEntity findMatchingSpecificParam(
            ThermalCommonParameterEntity commonParam,
            List<ThermalSpecificParametersEntity> specificParams,
            List<ThermalClusterCapacityEntity> thermalClusterCapacities
    ) {
        return specificParams.stream()
                .filter(s -> matchesSpecificParamForCluster(s, commonParam, thermalClusterCapacities))
                .findFirst()
                .orElse(null);
    }

    private boolean matchesSpecificParamForCluster(
            ThermalSpecificParametersEntity specificParam,
            ThermalCommonParameterEntity commonParam,
            List<ThermalClusterCapacityEntity> thermalClusterCapacities
    ) {
        if (specificParam.getThermalClusterRef() == null) {
            return false;
        }
        String specificClusterName = specificParam.getThermalClusterRef().getName();
        if (commonParam != null && commonParam.getThermalClusterRef() != null) {
            return Objects.equals(specificClusterName, commonParam.getThermalClusterRef().getName());
        }
        return thermalClusterCapacities.stream()
                .anyMatch(c -> c.getThermalClusterRef() != null
                        && Objects.equals(specificClusterName, c.getThermalClusterRef().getName()));
    }

    private void updateStartupCost(
            ThermalClusterGenerationDto dto,
            ThermalCommonParameterEntity commonParam,
            Integer startupFuel,
            Double efficiency,
            Double marginalCostValue
    ) {
        if (marginalCostValue == null) {
            return;
        }

        double startupFixCost = (commonParam != null && commonParam.getStartUpFixCost() != null)
                ? commonParam.getStartUpFixCost() : 0.0;
        // Formula: (startup_fuel * 1/MWH_TO_GJ * efficiency * marginal_cost) + startup_fix_cost, then multiplied by nominal capacity

        if (dto.getNominalCapacity() == null) {
            return;
        }

        double startupCost = (startupFuel * (1 / MWH_TO_GJ) * efficiency * marginalCostValue) + startupFixCost;
        log.info("Calculating startup cost for thermal cluster with nominal capacity: {} MWh, startup fuel: {} GJ, efficiency: {}, marginal cost: {}, startup fix cost: {}", dto.getNominalCapacity(), startupFuel, efficiency, marginalCostValue, startupFixCost);
        long startupCostInt = Math.round(startupCost);
        dto.setStartupCost(startupCostInt * dto.getNominalCapacity());
    }

    public record MarginalCostResult(Double value, Source source) {

        public enum Source {
            SPECIFIC_PARAM,
            FALLBACK_OM
        }
    }

    private MarginalCostResult getMarginalCost(
            ThermalSpecificParametersEntity specificParam,
            ThermalCostEntity economicCostTrajectories,
            String fuel,
            ThermalCommonParameterEntity commonParam,
            ThermalClusterGenerationDto dto
    ) {
        if (specificParam != null && specificParam.getMarginalCost() != null) {
            return new MarginalCostResult(
                    specificParam.getMarginalCost(),
                    MarginalCostResult.Source.SPECIFIC_PARAM
            );
        }

        Double marginalCostWithOm = computeFallbackMarginalCostWithOm(
                economicCostTrajectories, fuel, commonParam, dto
        );

        return new MarginalCostResult(
                marginalCostWithOm,
                MarginalCostResult.Source.FALLBACK_OM
        );
    }

    private Double computeFallbackMarginalCostWithOm(
            ThermalCostEntity economicCostTrajectories,
            String fuel,
            ThermalCommonParameterEntity commonParam,
            ThermalClusterGenerationDto dto
    ) {
        if (economicCostTrajectories == null) return null;
        TrajectoryEntity trajectory = economicCostTrajectories.getTrajectory();
        if (trajectory == null) return null;
        Double fuelCost = findFuelCost(trajectory, fuel);
        Double co2Cost = findCo2Cost(trajectory);
        Double efficiency = dto.getEfficiency();
        if (efficiency != null && efficiency > 1.0)
            efficiency = efficiency / 100.0;

        if (fuelCost != null && co2Cost != null && efficiency != null && efficiency != 0.0) {
            double omCost = (commonParam != null && commonParam.getOmCost() != null) ? commonParam.getOmCost() : 0.0;
            Double co2Value = dto.getCo2() != null ? dto.getCo2() : 0.0;
            // Formula: fuel / efficiency + CO2 cost * CO2 (calculated in computeCo2) + om_cost
            return (fuelCost / efficiency) + (co2Cost * co2Value) + omCost;
        }
        return null;
    }

    /**
     * Computes the market bid cost for a given thermal cluster generation based on the provided parameters.
     * The method sets the market bid cost in the DTO based on specific parameter matching or uses marginal cost with operation and
     * maintenance costs (omCost) as a fallback.
     *
     * @param dto Object representing the thermal cluster generation data where the computed market bid cost will be set.
     * @param commonParams List of entities containing common thermal parameters, including information on thermal cluster references.
     * @param specificParams List of entities containing specific thermal parameters used to derive the market bid cost.
     */
    public void computeMarketBidCost(ThermalClusterGenerationDto dto, List<ThermalCommonParameterEntity> commonParams,
                                     List<ThermalSpecificParametersEntity> specificParams) {

        Double marketBid = null;

        if (!commonParams.isEmpty()) {
            ThermalCommonParameterEntity firstCommon = commonParams.getFirst();
            marketBid = specificParams.stream()
                    .filter(s -> firstCommon.getThermalClusterRef() != null && s.getThermalClusterRef() != null
                            && Objects.equals(s.getThermalClusterRef().getName(), firstCommon.getThermalClusterRef().getName()))
                    .map(ThermalSpecificParametersEntity::getMarketBid)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }

        if (marketBid != null) {
            dto.setMarketBidCost(marketBid);
        } else if (dto.getMarginalCost() != null && MarginalCostResult.Source.FALLBACK_OM.equals(dto.getMarginalCostSource())) {
            dto.setMarketBidCost(dto.getMarginalCost());
        }
    }

    private Double findFuelCost(TrajectoryEntity trajectory, String fuel) {
        if (trajectory == null || trajectory.getThermalCosts() == null) return null;
        for (ThermalCostEntity costEntity : trajectory.getThermalCosts()) {
            ThermalCostTypeEntity type = costEntity.getThermalType();
            if (type != null && type.getFuel() != null && type.getFuel().equalsIgnoreCase(fuel) && costEntity.getCost() != null) {
                return costEntity.getCost();
            }
        }
        return null;
    }

    private Double findCo2Cost(TrajectoryEntity trajectory) {
        if (trajectory == null || trajectory.getThermalCosts() == null) return null;
        for (ThermalCostEntity costEntity : trajectory.getThermalCosts()) {
            ThermalCostTypeEntity type = costEntity.getThermalType();
            if (type != null && CO2.equalsIgnoreCase(type.getFuel()) && costEntity.getCost() != null) {
                return costEntity.getCost();
            }
        }
        return null;
    }
}
