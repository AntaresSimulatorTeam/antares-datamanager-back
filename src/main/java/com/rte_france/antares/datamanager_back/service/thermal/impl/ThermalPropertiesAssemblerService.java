package com.rte_france.antares.datamanager_back.service.thermal.impl;

import com.rte_france.antares.datamanager_back.dto.ThermalClusterGenerationDto;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalParamModulationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.rte_france.antares.datamanager_back.dto.TrajectoryType.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThermalPropertiesAssemblerService {

    private final ThermalGroupMappingService thermalGroupMappingService;

    private final ThermalParamModulationService thermalParamModulationService;

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
            List<ThermalCommonParameterEntity> commonsForRef = commonsByRef.getOrDefault(thermalClusterRef, List.of());
            List<ThermalSpecificParametersEntity> specificForRef = specificsByRef.getOrDefault(thermalClusterRef, List.of());

            ThermalClusterGenerationDto thermalClusterGenerationDto = computeClusterProperties(thermalCapacities, commonsForRef, specificForRef);
            
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

        if(areaClusterRefKey.area.equals("BE") && areaClusterRefKey.thermalClusterRef.getName().equals("Other Gas conventional old 2")) {
            log.info("stop ici");
        }
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

    private static LinkedHashMap<ThermalClusterRef, List<ThermalCommonParameterEntity>> extractCommonParamsByClusterRef(List<TrajectoryEntity> parameterTrajectories) {
        return parameterTrajectories.stream()
                .flatMap(t -> Optional.ofNullable(t.getThermalCommonParameters()).orElseGet(List::of).stream())
                .collect(Collectors.groupingBy(
                        ThermalCommonParameterEntity::getThermalClusterRef,
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
            List<ThermalClusterCapacityEntity> thermalClusterCapacities,
            List<ThermalCommonParameterEntity> thermalCommonParameters,
            List<ThermalSpecificParametersEntity> thermalSpecificParameters
    ) {
        ThermalClusterGenerationDto.ThermalClusterGenerationDtoBuilder thermalClusterGenerationDtoBuilder = ThermalClusterGenerationDto.builder();

        buildFromClusterCapacity(thermalClusterCapacities, thermalClusterGenerationDtoBuilder);
        buildFromCommonParameters(thermalCommonParameters, thermalClusterGenerationDtoBuilder);
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

    private void buildFromCommonParameters(List<ThermalCommonParameterEntity> thermalCommonParameters, ThermalClusterGenerationDto.ThermalClusterGenerationDtoBuilder builder) {
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
                .map(ThermalSpecificParametersEntity::getSpinning)
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
