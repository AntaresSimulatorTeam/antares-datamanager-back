package com.rte_france.antares.datamanager_back.service.thermal.impl;

import com.rte_france.antares.datamanager_back.dto.ThermalClusterGenerationDto;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.ThermalCostTypeRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalParamModulationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static com.rte_france.antares.datamanager_back.dto.TrajectoryType.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThermalPropertiesAssemblerService {

    private final ThermalGroupMappingService thermalGroupMappingService;

    private final ThermalParamModulationService thermalParamModulationService;

    private final ThermalCostTypeRepository thermalCostTypeRepository;

    public record AreaClusterRefKey(String area, ThermalClusterRef thermalClusterRef) {
    }

    /**
     * Builds thermal properties by {@code (area, cluster_ref)} from the given trajectories.
     * Uses {@code THERMAL_CAPACITY} rows (grouped by capacity.area + cluster_ref) and aggregates with
     * {@code THERMAL_PARAMETER} by cluster_ref
     *
     * @param study input study containing trajectories
     * @return map with {@link AreaClusterRefKey} as keys and {@link ThermalClusterGenerationDto} values
     * @throws NullPointerException     if {@code trajectories} is null
     * @throws IllegalArgumentException if a trajectory has an invalid {@link TrajectoryType}
     */


    public Map<AreaClusterRefKey, ThermalClusterGenerationDto> assembleForTrajectories(StudyEntity study)  {
        Set<TrajectoryEntity> trajectories = study.getTrajectories();
        Objects.requireNonNull(trajectories);

        var capacityTrajectories = trajectories.stream()
                .filter(Objects::nonNull)
                .filter(t -> THERMAL_CAPACITY.equals(TrajectoryType.valueOf(t.getType())))
                .toList();

        var commonTrajectories = trajectories.stream()
                .filter(Objects::nonNull)
                .filter(t -> THERMAL_TECHNICAL_COMMON_PARAMETER.equals(TrajectoryType.valueOf(t.getType())))
                .toList();
        var specificTrajectories = trajectories.stream()
                .filter(Objects::nonNull)
                .filter(t -> THERMAL_TECHNICAL_SPECIFIC_PARAMETER.equals(TrajectoryType.valueOf(t.getType())))
                .toList();

       List<String> splitedCmAndMrParamModulationTsFiles = thermalParamModulationService.createMatrixParamModulationTsFiles(study);

        var capacitiesByAreaRef = extractThermalCapacitiesByAreaClusterRef(capacityTrajectories);
        var commonsByRef = extractCommonParamsByClusterRef(commonTrajectories);
        var specificsByRef = extractSpecificParamsByClusterRef(specificTrajectories);

        var thermalClusterGenerationOutput = new LinkedHashMap<AreaClusterRefKey, ThermalClusterGenerationDto>();

        for (var entry : capacitiesByAreaRef.entrySet()) {
            AreaClusterRefKey areaClusterRefKey = entry.getKey();
            List<ThermalClusterCapacityEntity> thermalCapacities = entry.getValue();

            ThermalClusterRef thermalClusterRef = areaClusterRefKey.thermalClusterRef();
            String clusterName = thermalClusterRef != null ? thermalClusterRef.getName() : null;
            List<ThermalCommonParameterEntity> commonsForRef = clusterName == null
                    ? List.of()
                    : commonsByRef.getOrDefault(clusterName, List.of());
            List<ThermalSpecificParametersEntity> specificForRef = specificsByRef.getOrDefault(thermalClusterRef, List.of());

            ThermalClusterGenerationDto thermalClusterGenerationDto = computeClusterProperties(areaClusterRefKey, thermalCapacities, commonsForRef, specificForRef, commonTrajectories);

            // modulation param ts files ts
            List<String> modulationParamTsFiles = extractModulationParamTsFilesByAreaClusterRefKey(splitedCmAndMrParamModulationTsFiles, areaClusterRefKey);
            thermalClusterGenerationDto.setParamModulationTsList(modulationParamTsFiles);

            thermalClusterGenerationOutput.put(areaClusterRefKey, thermalClusterGenerationDto);
        }

        return thermalClusterGenerationOutput;
    }

    public static List<String>  extractModulationParamTsFilesByAreaClusterRefKey(List<String> splitedTsFileNameList, AreaClusterRefKey areaClusterRefKey) {

        //example file name : MR_BP23_T2_2022_dsr_AFL_2026-2027_BE_Other Gas conventional old 2.csv.6401800f-8425-49d5-a42b-e89cb1e8a293.arrow
        //area : BE
        //cluster name : Other Gas conventional old 2
        return splitedTsFileNameList.stream()
                .filter(fileName ->
                        fileName.contains("_" + areaClusterRefKey.area() + "_" + areaClusterRefKey.thermalClusterRef().getName() + ".csv"))
                .toList();


    }

    private static LinkedHashMap<ThermalClusterRef, List<ThermalSpecificParametersEntity>> extractSpecificParamsByClusterRef(List<TrajectoryEntity> specificTrajectories) {
        return specificTrajectories.stream()
                .flatMap(t -> Optional.ofNullable(t.getThermalSpecificParameters()).orElseGet(List::of).stream())
                .collect(Collectors.groupingBy(
                        ThermalSpecificParametersEntity::getThermalClusterRef,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private static LinkedHashMap<String, List<ThermalCommonParameterEntity>> extractCommonParamsByClusterRef(List<TrajectoryEntity> parameterTrajectories) {
        return parameterTrajectories.stream()
                .flatMap(t -> Optional.ofNullable(t.getThermalCommonParameters()).orElseGet(List::of).stream())
                // Only for common parameters: skip entries whose cluster ref has name_pemmdb == "NA"
                .filter(common -> {
                    ThermalClusterRef ref = common.getThermalClusterRef();
                    if (ref == null) return false;
                    String namePemmdb = ref.getNamePemmdb();
                    return namePemmdb == null || !"NA".equalsIgnoreCase(namePemmdb.trim());
                })
                .filter(common -> common.getThermalClusterRef().getName() != null)
                .collect(Collectors.groupingBy(
                        common -> common.getThermalClusterRef().getName(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    public static LinkedHashMap<AreaClusterRefKey, List<ThermalClusterCapacityEntity>> extractThermalCapacitiesByAreaClusterRef(List<TrajectoryEntity> capacityTrajs) {
        return capacityTrajs.stream()
                .flatMap(t -> Optional.ofNullable(t.getThermalClusterCapacities())
                        .orElseGet(List::of).stream()
                        .map(cap -> Map.entry(
                                new AreaClusterRefKey(cap.getArea(), cap.getThermalClusterRef()),
                                cap
                        )))
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        LinkedHashMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                ));
    }

    private ThermalClusterGenerationDto computeClusterProperties(
            AreaClusterRefKey areaClusterRefKey,
            List<ThermalClusterCapacityEntity> thermalClusterCapacities,
            List<ThermalCommonParameterEntity> thermalCommonParameters,
            List<ThermalSpecificParametersEntity> thermalSpecificParameters,
            List<TrajectoryEntity> commonTrajectories
    ) {
        ThermalClusterGenerationDto.ThermalClusterGenerationDtoBuilder thermalClusterGenerationDtoBuilder = ThermalClusterGenerationDto.builder();

        buildFromClusterCapacity(thermalClusterCapacities, thermalClusterGenerationDtoBuilder);
        buildFromCommonParameters(areaClusterRefKey, thermalCommonParameters, thermalClusterGenerationDtoBuilder, commonTrajectories);
        buildFromSpecificParameters(thermalSpecificParameters, thermalClusterGenerationDtoBuilder);

        return thermalClusterGenerationDtoBuilder.build();
    }


    private void buildFromClusterCapacity(List<ThermalClusterCapacityEntity> thermalClusterCapacities, ThermalClusterGenerationDto.ThermalClusterGenerationDtoBuilder builder) {

        // max POWER capacity
        OptionalDouble maxPowerOpt = thermalClusterCapacities.stream()
                .filter(cap -> cap.getCategory() == ThermalCategoryEnum.POWER)
                .mapToDouble(ThermalClusterCapacityEntity::getValue)
                .max();
        //max unit count
        OptionalDouble unitCountOpt = thermalClusterCapacities.stream()
                .filter(cap -> cap.getCategory() == ThermalCategoryEnum.NUMBER)
                .mapToDouble(ThermalClusterCapacityEntity::getValue)
                .max();
        // nominal capacity (max POWER / unitCount) ---
        if (maxPowerOpt.isPresent()) {
            double maxPower = maxPowerOpt.getAsDouble();
            double nominalCapacity = maxPower;

            if (unitCountOpt.isPresent() && unitCountOpt.getAsDouble() != 0.0) {
                nominalCapacity = maxPower / unitCountOpt.getAsDouble();
            }

            nominalCapacity = Math.round(nominalCapacity * 10.0) / 10.0;
            builder.nominalCapacity(nominalCapacity);
        }
        // enabled
        boolean enabled = maxPowerOpt.isPresent()
                && maxPowerOpt.getAsDouble() != 0.0
                && thermalClusterCapacities.stream()
                .anyMatch(cap -> Boolean.TRUE.equals(cap.getToUse()));

        builder.enabled(enabled);

        // unit_count
        unitCountOpt.ifPresent(unitCount -> builder.unitCount((int) unitCount));


        // group
        thermalClusterCapacities.stream()
                .map(ThermalClusterCapacityEntity::getThermalClusterRef)
                .map(ThermalClusterRef::getName)
                .map(thermalGroupMappingService::toGroup)
                .flatMap(Optional::stream)
                .findFirst()
                .ifPresent(builder::group);
    }

    private void buildFromCommonParameters(AreaClusterRefKey areaClusterRefKey, List<ThermalCommonParameterEntity> thermalCommonParameters, ThermalClusterGenerationDto.ThermalClusterGenerationDtoBuilder builder, List<TrajectoryEntity> commonTrajectories) {
        // min_stable_power
        var nominalCapacity = builder.build().getNominalCapacity();
        if (nominalCapacity != null) {
            thermalCommonParameters.stream()
                    .mapToDouble(ThermalCommonParameterEntity::getMinStableGenerationDefault)
                    .findFirst()
                    .ifPresent(minStableGen -> builder.minStablePower(minStableGen * nominalCapacity));
        }

        // min_up_time
        thermalCommonParameters.stream()
                .mapToDouble(ThermalCommonParameterEntity::getMinUpTime)
                .findFirst()
                .ifPresent(minUpTime -> builder.minUpTime((int) minUpTime));

        // min_down_time
        thermalCommonParameters.stream()
                .mapToDouble(ThermalCommonParameterEntity::getMinDownTime)
                .findFirst()
                .ifPresent(minDownTime -> builder.minDownTime((int) minDownTime));

        // efficiency
        thermalCommonParameters.stream()
                .mapToDouble(thermalCommonParam -> thermalCommonParam.getEfficiencyDefault() * 100) // convert to percentage
                .findFirst()
                .ifPresent(builder::efficiency);

        // variable_o_m_cost
        thermalCommonParameters.stream()
                .mapToDouble(ThermalCommonParameterEntity::getOmCost)
                .findFirst()
                .ifPresent(builder::variableOMCost);
        //CO2
        thermalCommonParameters.stream()
                .findFirst()
                .ifPresent(thermalCommonParameterEntity -> {
                    if (thermalCommonParameterEntity.getCo2() != null && thermalCommonParameterEntity.getCo2() != 0.0) {
                        builder.co2(thermalCommonParameterEntity.getCo2() * 0.0036);
                    } else {
                        computeFallbackCo2(areaClusterRefKey, thermalCommonParameterEntity, builder, commonTrajectories);
                    }
                });

        //FO rate
        thermalCommonParameters.stream()
                .mapToDouble(ThermalCommonParameterEntity::getFoRateDefault)
                .findFirst()
                .ifPresent(builder::foCommonRate);

        //FO duration
        thermalCommonParameters.stream()
                .mapToDouble(ThermalCommonParameterEntity::getFoDurationDefault)
                .findFirst()
                .ifPresent(builder::foCommonDuration);

        //PO rate
        thermalCommonParameters.stream()
                .mapToDouble(ThermalCommonParameterEntity::getPoWinterDefault)
                .findFirst()
                .ifPresent(builder::poCommonRate);
        //PO duration
        thermalCommonParameters.stream()
                .mapToDouble(ThermalCommonParameterEntity::getPoDurationDefault)
                .findFirst()
                .ifPresent(builder::poCommonDuration);


    }

    private void computeFallbackCo2(AreaClusterRefKey areaClusterRefKey, ThermalCommonParameterEntity commonParam, ThermalClusterGenerationDto.ThermalClusterGenerationDtoBuilder builder, List<TrajectoryEntity> commonTrajectories) {
        String fuel = commonParam.getFuel();
        if (fuel == null) return;

        Double efficiency = builder.build().getEfficiency();
        if (efficiency == null || efficiency == 0.0) return;

        for (TrajectoryEntity trajectory : commonTrajectories) {
            String horizon = trajectory.getHorizon();
            if (horizon == null) continue;

            Integer horizonYear;
            try {
                horizonYear = Integer.parseInt(horizon);
            } catch (NumberFormatException e) {
                continue;
            }

            Optional<ThermalEconomicCo2Entity> economicCo2Opt = Optional.ofNullable(trajectory.getThermalEconomicCo2s())
                    .orElseGet(List::of).stream()
                    .filter(e -> fuel.equals(e.getFuel()) && horizonYear.equals(e.getYear()))
                    .findFirst();

            if (economicCo2Opt.isPresent()) {
                BigDecimal co2EmissionFuel = economicCo2Opt.get().getCo2EmissionFuel();
                if (co2EmissionFuel != null) {
                    Optional<ThermalCostTypeEntity> costTypeOpt = thermalCostTypeRepository.findThermalCostTypeEntityByFuelAndCountry(fuel, areaClusterRefKey.area());
                    if (costTypeOpt.isPresent() && costTypeOpt.get().getRatioNcvHcv() != null && costTypeOpt.get().getRatioNcvHcv() != 0.0) {
                        double ratioNcvHcv = costTypeOpt.get().getRatioNcvHcv();
                        // Formula: (co2EmissionFuel / 1000) / (efficiency / 100) / ratioNcvHcv
                        double co2Value = (co2EmissionFuel.doubleValue() / 1000.0) / (efficiency / 100.0) / ratioNcvHcv;
                        builder.co2(co2Value);
                        return;
                    }
                }
            }
        }
    }

    private void buildFromSpecificParameters(List<ThermalSpecificParametersEntity> thermalSpecificParameters, ThermalClusterGenerationDto.ThermalClusterGenerationDtoBuilder builder) {
        // min_stable_power
        var nominalCapacity = builder.build().getNominalCapacity();
        if (nominalCapacity != null) {
            thermalSpecificParameters.stream()
                    .mapToDouble(ThermalSpecificParametersEntity::getMinStableGeneration)
                    .findFirst()
                    .ifPresent(minStableGen -> builder.minStablePower(minStableGen * nominalCapacity));
        }
        //spinning
        thermalSpecificParameters.stream()
                .map(thermalSpecificParam -> thermalSpecificParam.getSpinning() * 100)
                .findFirst()
                .ifPresent(builder::spinning);
        //efficiency
        thermalSpecificParameters.stream()
                .mapToDouble(thermalSpecificParam -> thermalSpecificParam.getEfficiency() * 100)
                .findFirst()
                .ifPresent(builder::efficiency);

        //FO duration
        thermalSpecificParameters.stream()
                .mapToDouble(ThermalSpecificParametersEntity::getFoDuration)
                .findFirst()
                .ifPresent(builder::foDuration);

        //PO duration
        thermalSpecificParameters.stream()
                .mapToDouble(ThermalSpecificParametersEntity::getPoDuration)
                .findFirst()
                .ifPresent(builder::poDuration);
        //FO Monthly rate
        thermalSpecificParameters.stream()
                .findFirst()
                .ifPresent(param -> {
                    List<Double> forcedOutageMonthly = List.of(
                            param.getF1(),
                            param.getF2(),
                            param.getF3(),
                            param.getF4(),
                            param.getF5(),
                            param.getF6(),
                            param.getF7(),
                            param.getF8(),
                            param.getF9(),
                            param.getF10(),
                            param.getF11(),
                            param.getF12()
                    );
                    builder.foMonthlyRate(forcedOutageMonthly);
                });

        //PO Monthly rate
        thermalSpecificParameters.stream()
                .findFirst()
                .ifPresent(param -> {
                    List<Double> plannedOutageMonthly = List.of(
                            param.getP1(),
                            param.getP2(),
                            param.getP3(),
                            param.getP4(),
                            param.getP5(),
                            param.getP6(),
                            param.getP7(),
                            param.getP8(),
                            param.getP9(),
                            param.getP10(),
                            param.getP11(),
                            param.getP12()
                    );
                    builder.poMonthlyRate(plannedOutageMonthly);
                });
        //NPO_MAX_winter
        thermalSpecificParameters.stream().mapToDouble(ThermalSpecificParametersEntity::getNpoMaxWinter)
                .findFirst()
                .ifPresent(builder::npoMaxWinter);
        //NPO_MAX_summer
        thermalSpecificParameters.stream().mapToDouble(ThermalSpecificParametersEntity::getNpoMaxSummer)
                .findFirst()
                .ifPresent(builder::npoMaxSummer);
        //nb_unit
        thermalSpecificParameters.stream().mapToInt(ThermalSpecificParametersEntity::getNbUnit)
                .findFirst()
                .ifPresent(builder::nbUnit);

    }
}
