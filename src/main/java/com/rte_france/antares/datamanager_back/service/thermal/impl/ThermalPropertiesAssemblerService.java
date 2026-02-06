package com.rte_france.antares.datamanager_back.service.thermal.impl;

import com.rte_france.antares.datamanager_back.dto.ThermalClusterGenerationDto;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.ThermalCostTypeRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalParamModulationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.rte_france.antares.datamanager_back.dto.TrajectoryType.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThermalPropertiesAssemblerService {

    private final ThermalGroupMappingService thermalGroupMappingService;

    private final ThermalParamModulationService thermalParamModulationService;

    private final ThermalCostTypeRepository thermalCostTypeRepository;

    private final ThermalCostAssembler thermalCostAssembler;

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


    public Map<AreaClusterRefKey, ThermalClusterGenerationDto> assembleForTrajectories(StudyEntity study) {
        Integer studyId = study != null ? study.getId() : null;
        MDC.put("studyId", Objects.toString(studyId, "null"));
        log.info("Start assembling thermal properties for studyId={}", studyId);

        Set<TrajectoryEntity> trajectories = study.getTrajectories();
        Objects.requireNonNull(trajectories);

        Map<AreaClusterRefKey, ThermalClusterGenerationDto> thermalClusterGenerationOutput = new LinkedHashMap<>();

        try {
            var capacityTrajectories = trajectories.stream()
                    .filter(Objects::nonNull)
                    .filter(t -> THERMAL_CAPACITY.equals(TrajectoryType.valueOf(t.getType())))
                    .toList();

            var commonTrajectories = trajectories.stream()
                    .filter(Objects::nonNull)
                    .filter(t -> THERMAL_TECHNICAL_COMMON_PARAMETER.equals(TrajectoryType.valueOf(t.getType()))
                            || THERMAL_ECONOMIC_PARAMETER.equals(TrajectoryType.valueOf(t.getType())))
                    .toList();
            var specificTrajectories = trajectories.stream()
                    .filter(Objects::nonNull)
                    .filter(t -> THERMAL_TECHNICAL_SPECIFIC_PARAMETER.equals(TrajectoryType.valueOf(t.getType())))
                    .toList();

            List<String> splitedCmAndMrParamModulationTsFiles = thermalParamModulationService.createMatrixParamModulationTsFiles(study);

            var capacitiesByAreaRef = extractThermalCapacitiesByAreaClusterRef(capacityTrajectories);
            var commonsByRef = extractCommonParamsByClusterRef(commonTrajectories);
            var specificsByRef = extractSpecificParamsByClusterRef(specificTrajectories);

            for (var entry : capacitiesByAreaRef.entrySet()) {
                AreaClusterRefKey areaClusterRefKey = entry.getKey();
                List<ThermalClusterCapacityEntity> thermalCapacities = entry.getValue();

                ThermalClusterRef thermalClusterRef = areaClusterRefKey.thermalClusterRef();
                String clusterName = thermalClusterRef != null ? thermalClusterRef.getName() : null;
                List<ThermalCommonParameterEntity> commonsForRef = clusterName == null
                        ? List.of()
                        : commonsByRef.getOrDefault(clusterName, List.of());
                List<ThermalSpecificParametersEntity> specificForRef = specificsByRef.getOrDefault(thermalClusterRef, List.of());

                ThermalClusterGenerationDto thermalClusterGenerationDto = computeClusterProperties(thermalCapacities, commonsForRef, specificForRef, commonTrajectories);

                // modulation param ts files ts
                List<String> modulationParamTsFiles = extractModulationParamTsFilesByAreaClusterRefKey(splitedCmAndMrParamModulationTsFiles, areaClusterRefKey);
                thermalClusterGenerationDto.setParamModulationTsList(modulationParamTsFiles);

                thermalClusterGenerationOutput.put(areaClusterRefKey, thermalClusterGenerationDto);

                log.info("Assembled cluster '{}' for area '{}' (studyId={})", clusterName, areaClusterRefKey.area(), studyId);
            }
        } finally {
            log.info("Finished assembling {} thermal cluster(s) for studyId={}", thermalClusterGenerationOutput.size(), studyId);
            MDC.remove("studyId");
        }

        return thermalClusterGenerationOutput;
    }

    public static List<String> extractModulationParamTsFilesByAreaClusterRefKey(List<String> splitedTsFileNameList, AreaClusterRefKey areaClusterRefKey) {

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
        log.info("Extracting common parameters by cluster ref from {} trajectories", parameterTrajectories.size());
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
        log.info("Extracting thermal capacities by area and cluster ref from {} trajectories", capacityTrajs.size());
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

    /**
     * Renvoie le premier Double non-null de la collection, converti en double.
     * NPE-safe (collection null, éléments null, valeur mappée null).
     */
    private static <T> OptionalDouble firstNonNullDouble(Collection<T> source, Function<T, Double> mapper) {
        if (source == null || source.isEmpty()) return OptionalDouble.empty();

        return source.stream()
                .filter(Objects::nonNull)
                .map(mapper)                  // Stream<Double>
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .findFirst();
    }

    private ThermalClusterGenerationDto computeClusterProperties(
            List<ThermalClusterCapacityEntity> thermalClusterCapacities,
            List<ThermalCommonParameterEntity> thermalCommonParameters,
            List<ThermalSpecificParametersEntity> thermalSpecificParameters,
            List<TrajectoryEntity> commonTrajectories
    ) {
        ThermalClusterGenerationDto.ThermalClusterGenerationDtoBuilder thermalClusterGenerationDtoBuilder = ThermalClusterGenerationDto.builder();

        buildFromClusterCapacity(thermalClusterCapacities, thermalClusterGenerationDtoBuilder);
        buildFromCommonParameters(thermalCommonParameters, thermalClusterGenerationDtoBuilder);
        buildFromSpecificParameters(thermalSpecificParameters, thermalClusterGenerationDtoBuilder);

        ThermalCommonParameterEntity commonParam = thermalCommonParameters.stream().findFirst().orElse(null);
        String fuel = commonParam != null ? commonParam.getFuel() : null;

        if (fuel == null) {
            fuel = thermalClusterCapacities.stream()
                    .map(ThermalClusterCapacityEntity::getFuel)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }

        Double ratioNcvHcv = null;
        if (fuel != null) {
            ratioNcvHcv = thermalCostTypeRepository.findByFuelIgnoreCase(fuel).stream()
                    .map(ThermalCostTypeEntity::getRatioNcvHcv)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }

        ThermalClusterGenerationDto dto = thermalClusterGenerationDtoBuilder.build();
        thermalSpecificParameters.stream().findFirst().ifPresent(specificParam -> {
            if (specificParam.getMarginalCost() != null) {
                dto.setMarginalCost(specificParam.getMarginalCost());
            }
        });

        thermalCostAssembler.computeCo2(dto, commonParam, fuel, commonTrajectories, ratioNcvHcv);
        thermalCostAssembler.computeStartupCost(dto, commonParam, fuel, thermalSpecificParameters, thermalClusterCapacities, commonTrajectories);
        thermalCostAssembler.computeMarketBidCost(dto, commonParam, thermalSpecificParameters);

        return dto;
    }


    private void buildFromClusterCapacity(List<ThermalClusterCapacityEntity> thermalClusterCapacities, ThermalClusterGenerationDto.ThermalClusterGenerationDtoBuilder builder) {
        log.info("Building cluster properties from {} thermal capacities", thermalClusterCapacities.size());
        // max POWER capacity
        OptionalDouble maxPowerOpt = thermalClusterCapacities.stream()
                .filter(Objects::nonNull)
                .filter(cap -> cap.getCategory() == ThermalCategoryEnum.POWER)
                .map(ThermalClusterCapacityEntity::getValue)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max();

        // max unit count
        OptionalDouble unitCountOpt = thermalClusterCapacities.stream()
                .filter(Objects::nonNull)
                .filter(cap -> cap.getCategory() == ThermalCategoryEnum.NUMBER)
                .map(ThermalClusterCapacityEntity::getValue)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
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

    private void buildFromCommonParameters(List<ThermalCommonParameterEntity> thermalCommonParameters, ThermalClusterGenerationDto.ThermalClusterGenerationDtoBuilder builder) {

        log.info("Building cluster properties from {} common parameter entries", thermalCommonParameters.size());
        // min_stable_power
        var nominalCapacity = builder.build().getNominalCapacity();
        if (nominalCapacity != null) {
            firstNonNullDouble(thermalCommonParameters, ThermalCommonParameterEntity::getMinStableGenerationDefault)
                    .ifPresent(minStableGen -> builder.minStablePower(round(minStableGen * nominalCapacity)));
        }

        // min_up_time
        firstNonNullDouble(thermalCommonParameters, ThermalCommonParameterEntity::getMinUpTime)
                .ifPresent(minUpTime -> builder.minUpTime((int) minUpTime));

        // min_down_time
        firstNonNullDouble(thermalCommonParameters, ThermalCommonParameterEntity::getMinDownTime)
                .ifPresent(minDownTime -> builder.minDownTime((int) minDownTime));

        // efficiency (convert to percentage)
        firstNonNullDouble(thermalCommonParameters, p ->
                p.getEfficiencyDefault() == null ? null : p.getEfficiencyDefault() * 100
        ).ifPresent(efficiency -> builder.efficiency(round(efficiency)));

        // variable_o_m_cost
        firstNonNullDouble(thermalCommonParameters, ThermalCommonParameterEntity::getOmCost)
                .ifPresent(omCost -> builder.variableOMCost(round(omCost)));

        // FO rate
        firstNonNullDouble(thermalCommonParameters, ThermalCommonParameterEntity::getFoRateDefault)
                .ifPresent(foRate -> builder.foCommonRate(round(foRate)));

        // FO duration
        firstNonNullDouble(thermalCommonParameters, ThermalCommonParameterEntity::getFoDurationDefault)
                .ifPresent(foDuration -> builder.foCommonDuration(round(foDuration)));

        // PO rate
        firstNonNullDouble(thermalCommonParameters, ThermalCommonParameterEntity::getPoWinterDefault)
                .ifPresent(poRate -> builder.poCommonRate(round(poRate)));

        // PO duration
        firstNonNullDouble(thermalCommonParameters, ThermalCommonParameterEntity::getPoDurationDefault)
                .ifPresent(poDuration -> builder.poCommonDuration(round(poDuration)));
    }

    private void buildFromSpecificParameters(List<ThermalSpecificParametersEntity> thermalSpecificParameters, ThermalClusterGenerationDto.ThermalClusterGenerationDtoBuilder builder) {

        log.info("Building cluster properties from {} specific parameter entries", thermalSpecificParameters.size());
        // min_stable_power
        var nominalCapacity = builder.build().getNominalCapacity();
        if (nominalCapacity != null) {
            thermalSpecificParameters.stream()
                    .map(ThermalSpecificParametersEntity::getMinStableGeneration)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .ifPresent(minStableGen -> builder.minStablePower(round(minStableGen * nominalCapacity)));
        }
        //spinning
        thermalSpecificParameters.stream()
                .map(ThermalSpecificParametersEntity::getSpinning)
                .filter(Objects::nonNull)
                .findFirst()
                .ifPresent(spinning -> builder.spinning(round(spinning * 100)));
        //efficiency
        thermalSpecificParameters.stream()
                .map(ThermalSpecificParametersEntity::getEfficiency)
                .filter(Objects::nonNull)
                .findFirst()
                .ifPresent(efficiency -> builder.efficiency(round(efficiency * 100)));

        //FO duration
        thermalSpecificParameters.stream()
                .map(ThermalSpecificParametersEntity::getFoDuration)
                .filter(Objects::nonNull)
                .findFirst()
                .ifPresent(foDuration -> builder.foDuration(round(foDuration)));

        //PO duration
        thermalSpecificParameters.stream()
                .map(ThermalSpecificParametersEntity::getPoDuration)
                .filter(Objects::nonNull)
                .findFirst()
                .ifPresent(poDuration -> builder.poDuration(round(poDuration)));
        //FO Monthly rate
        thermalSpecificParameters.stream()
                .findFirst()
                .ifPresent(param -> {
                    if (param.getF1() != null) {
                        List<Double> forcedOutageMonthly = Arrays.asList(
                                round(param.getF1()), round(param.getF2()), round(param.getF3()), round(param.getF4()),
                                round(param.getF5()), round(param.getF6()), round(param.getF7()), round(param.getF8()),
                                round(param.getF9()), round(param.getF10()), round(param.getF11()), round(param.getF12())
                        );
                        builder.foMonthlyRate(forcedOutageMonthly);
                    }
                });

        //PO Monthly rate
        thermalSpecificParameters.stream()
                .findFirst()
                .ifPresent(param -> {
                    if (param.getP1() != null) {
                        List<Double> plannedOutageMonthly = Arrays.asList(
                                round(param.getP1()), round(param.getP2()), round(param.getP3()), round(param.getP4()),
                                round(param.getP5()), round(param.getP6()), round(param.getP7()), round(param.getP8()),
                                round(param.getP9()), round(param.getP10()), round(param.getP11()), round(param.getP12())
                        );
                        builder.poMonthlyRate(plannedOutageMonthly);
                    }
                });
        //NPO_MAX_winter
        thermalSpecificParameters.stream()
                .map(ThermalSpecificParametersEntity::getNpoMaxWinter)
                .filter(Objects::nonNull)
                .findFirst()
                .ifPresent(npoMaxWinter -> builder.npoMaxWinter(round(npoMaxWinter.doubleValue())));
        //NPO_MAX_summer
        thermalSpecificParameters.stream()
                .map(ThermalSpecificParametersEntity::getNpoMaxSummer)
                .filter(Objects::nonNull)
                .findFirst()
                .ifPresent(npoMaxSummer -> builder.npoMaxSummer(round(npoMaxSummer.doubleValue())));
        //nb_unit
        thermalSpecificParameters.stream()
                .map(ThermalSpecificParametersEntity::getNbUnit)
                .filter(Objects::nonNull)
                .findFirst()
                .ifPresent(builder::nbUnit);

    }

    private Double round(Double value) {
        if (value == null) {
            return null;
        }
        return java.math.BigDecimal.valueOf(value).setScale(3, java.math.RoundingMode.HALF_UP).doubleValue();
    }
}
