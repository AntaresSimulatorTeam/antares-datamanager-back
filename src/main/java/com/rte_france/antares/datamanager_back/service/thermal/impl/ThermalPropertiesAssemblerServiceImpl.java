package com.rte_france.antares.datamanager_back.service.thermal.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.*;
import com.rte_france.antares.datamanager_back.mapper.ThermalMeMapper;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.thermal.AreaClusterRefKey;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalParamModulationService;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalPropertiesAssemblerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.rte_france.antares.datamanager_back.dto.TrajectoryType.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThermalPropertiesAssemblerServiceImpl implements ThermalPropertiesAssemblerService {

    private final ThermalGroupMappingService thermalGroupMappingService;

    private final ThermalParamModulationService thermalParamModulationService;

    private final ThermalCostAssembler thermalCostAssembler;

    private final AntaresDataManagerProperties antaresDataManagerProperties;
    
    private static final String TIMESTEP_HOURLY = "hourly";
    private static final String FILE_EXTENSION_XLSX = ".xlsx";

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
        if (study == null) {
            return Collections.emptyMap();
        }
        Integer studyId = study.getId();
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
                    .filter(t -> THERMAL_TECHNICAL_COMMON_PARAMETER.equals(TrajectoryType.valueOf(t.getType())))
                    .toList();
            var economicTrajectory = trajectories.stream()
                    .filter(Objects::nonNull)
                    .filter(t -> THERMAL_ECONOMIC_PARAMETER.equals(TrajectoryType.valueOf(t.getType())))
                    .findFirst()
                    .orElse(null);
            var economicCostTrajectory = trajectories.stream()
                    .filter(Objects::nonNull)
                    .filter(t -> THERMAL_ECONOMIC_COST_PARAMETER.equals(TrajectoryType.valueOf(t.getType())))
                    .findFirst()
                    .orElse(null);

            ThermalEconomicEnerContentEntity economicEnerContentParam = Optional.ofNullable(economicTrajectory)
                    .map(TrajectoryEntity::getThermalEconomicEnerContents)
                    .orElseGet(List::of)
                    .stream()
                    .filter(e -> "mwht/gj".equalsIgnoreCase(e.getUnit()))
                    .findFirst()
                    .orElse(null);

            if (economicEnerContentParam != null) {
                economicEnerContentParam.setTrajectory(economicTrajectory);
            }


            var specificTrajectories = trajectories.stream()
                    .filter(Objects::nonNull)
                    .filter(t -> THERMAL_TECHNICAL_SPECIFIC_PARAMETER.equals(TrajectoryType.valueOf(t.getType())))
                    .toList();

            // modulation param ts files ts
            List<String> splitCmAndMrParamModulationTsFiles = thermalParamModulationService.createMatrixParamModulationTsFiles(study);

            //clusterRef IP: name, NA, 1
            var capacitiesByAreaRef = extractThermalCapacitiesByAreaClusterRef(capacityTrajectories); //by  area_cluster_ref (area + cluster ref)
            //clusterRef common: name, namePEMMDB, null
            var commonsParamByClusterName = extractCommonParamsByClusterRef(commonTrajectories); // by cluster name




            for (var entry : capacitiesByAreaRef.entrySet()) {
                AreaClusterRefKey areaClusterRefKey = entry.getKey();
                ThermalClusterRef thermalClusterRef = areaClusterRefKey.thermalClusterRef();
                String capacityClusterName = thermalClusterRef != null ? thermalClusterRef.getName() : null;

                List<ThermalCommonParameterEntity> commonsForRef = capacityClusterName == null ? List.of() : commonsParamByClusterName.getOrDefault(capacityClusterName, List.of());
                //get list of specific param for the cluster ref : by matching name and technology is null
                List<ThermalSpecificParametersEntity> specificForRef = specificTrajectories.stream()
                        .flatMap(t -> Optional.ofNullable(t.getThermalSpecificParameters()).orElseGet(List::of).stream())
                        .filter(specific -> specific.getCluster() != null
                               && Objects.equals(specific.getCluster(), capacityClusterName)
                               && (specific.getArea() == null || specific.getArea().isBlank() || specific.getArea().equalsIgnoreCase(areaClusterRefKey.area()))
                        )
                .toList();

                List<ThermalClusterCapacityEntity> thermalCapacities = entry.getValue();

                ThermalClusterGenerationDto thermalClusterGenerationDto = computeClusterProperties(thermalCapacities, commonsForRef, specificForRef, economicEnerContentParam, economicCostTrajectory);

                List<String> modulationParamTsFiles = extractModulationParamTsFilesByAreaClusterRefKey(splitCmAndMrParamModulationTsFiles, areaClusterRefKey);
                thermalClusterGenerationDto.setParamModulationTsList(modulationParamTsFiles);

                thermalClusterGenerationOutput.put(areaClusterRefKey, thermalClusterGenerationDto);

                log.info("Assembled cluster '{}' for area '{}' (studyId={})", capacityClusterName, areaClusterRefKey.area(), studyId);
            }
        } finally {
            log.info("Finished assembling {} thermal cluster(s) for studyId={}", thermalClusterGenerationOutput.size(), studyId);
            MDC.remove("studyId");
        }

        return thermalClusterGenerationOutput;
    }

    @Override
    public Map<String, ThermalMEClusterGenerationDto> assembleThermalMeProperties(TrajectoryEntity trajectoryEntity) {
        if (trajectoryEntity == null || trajectoryEntity.getThermalMeEntities() == null) {
            return Collections.emptyMap();
        }

        String trajectoryFileName = trajectoryEntity.getFileName();
        List<ThermalMeEntity> thermalMeEntities = trajectoryEntity.getThermalMeEntities();

        Path baseDirectory = Path.of(
                antaresDataManagerProperties.getNasDirectory(),
                antaresDataManagerProperties.getTrajectoryFilePath(),
                antaresDataManagerProperties.getThermalMeDirectory()
        );

        Map<String, ThermalMEClusterGenerationDto> dtosByCluster = new LinkedHashMap<>();
        Map<Path, List<String>> modulationFilesToClusters = new LinkedHashMap<>();

        for (ThermalMeEntity entity : thermalMeEntities) {
            String clusterName = entity.getNode().toUpperCase(Locale.ROOT) + "_" + entity.getClusterName().toUpperCase(Locale.ROOT);
            ThermalMEClusterGenerationDto dto = ThermalMeMapper.mapToThermalMeGenerationDTO(entity);
            dtosByCluster.put(clusterName, dto);

            buildThermalMeClusterDto(modulationFilesToClusters, entity, trajectoryFileName, baseDirectory);
        }
        String horizon = extractHorizonYear(trajectoryEntity.getHorizon());
        try {
            for (Map.Entry<Path, List<String>> entry : modulationFilesToClusters.entrySet()) {
                Path fullPath = entry.getKey();
                List<String> clusterNames = entry.getValue();
                Map<String, List<String>> clusterArrowFiles = thermalParamModulationService.createThermalMeModulationArrowFile(horizon, clusterNames, fullPath, trajectoryFileName);

                for (Map.Entry<String, List<String>> arrowEntry : clusterArrowFiles.entrySet()) {
                    String clusterName = arrowEntry.getKey();
                    List<String> arrowFiles = arrowEntry.getValue();
                    ThermalMEClusterGenerationDto dto = dtosByCluster.get(clusterName);
                    if (dto != null && arrowFiles != null && !arrowFiles.isEmpty()) {
                        if (dto.getTsList() == null) {
                            dto.setTsList(new ArrayList<>());
                        }
                        dto.getTsList().addAll(arrowFiles);
                    }
                }
            }
        } catch (IOException e) {
            throw TechnicalException.builder()
                    .message("Failed to create thermal ME modulation arrow files")
                    .cause(e)
                    .build();
        }

        return dtosByCluster;
    }

    private Map<Path, List<String>> buildThermalMeClusterDto(
            Map<Path, List<String>> modulationFilesToClusters,
            ThermalMeEntity entity,
            String trajectoryFileName,
            Path baseDirectory
    ) {
        boolean isMarginalCostHourly = TIMESTEP_HOURLY.equalsIgnoreCase(entity.getMarginalCostTimestep());
        boolean isMarketBidCostHourly = TIMESTEP_HOURLY.equalsIgnoreCase(entity.getMarketBidCostTimestep());
        boolean isCapacityHourly = TIMESTEP_HOURLY.equalsIgnoreCase(entity.getCmTimestep());
        boolean isMinGenHourly = TIMESTEP_HOURLY.equalsIgnoreCase(entity.getMrTimestep());

        String clusterName = entity.getNode().toUpperCase(Locale.ROOT) + "_" + entity.getClusterName().toUpperCase(Locale.ROOT);

        if (isMarginalCostHourly) {
            String folder = antaresDataManagerProperties.getThermalMeMarginalCostModulationDirectory();
            Path fullPath = resolveModulationFullPath(baseDirectory, folder, trajectoryFileName);
            if (fullPath != null) {
                modulationFilesToClusters.computeIfAbsent(fullPath, k -> new ArrayList<>()).add(clusterName);
            }
        }

        if (isMarketBidCostHourly) {
            String folder = antaresDataManagerProperties.getThermalMeMarketBidCostModulationDirectory();
            Path fullPath = resolveModulationFullPath(baseDirectory, folder, trajectoryFileName);
            if (fullPath != null) {
                modulationFilesToClusters.computeIfAbsent(fullPath, k -> new ArrayList<>()).add(clusterName);
            }
        }

        if (isCapacityHourly) {
            String folder = antaresDataManagerProperties.getThermalMeCapacityModulationDirectory();
            Path fullPath = resolveModulationFullPath(baseDirectory, folder, trajectoryFileName);
            if (fullPath != null) {
                modulationFilesToClusters.computeIfAbsent(fullPath, k -> new ArrayList<>()).add(clusterName);
            }
        }

        if (isMinGenHourly) {
            String folder = antaresDataManagerProperties.getThermalMeMustRunModulationDirectory();
            Path fullPath = resolveModulationFullPath(baseDirectory, folder, trajectoryFileName);
            if (fullPath != null) {
                modulationFilesToClusters.computeIfAbsent(fullPath, k -> new ArrayList<>()).add(clusterName);
            }
        }

        return modulationFilesToClusters;
    }

    private String extractHorizonYear(String horizon) {
        return horizon != null && horizon.contains("-") ? horizon.split("-")[1] : horizon;
    }

    /**
     * Vérifie l'existence du fichier dans le répertoire cible et renvoie son nom (ou null s'il n'existe pas).
     */
    private Path resolveModulationFullPath(Path baseDirectory, String folderName, String trajectoryFileName) {
        if (folderName == null || trajectoryFileName == null) {
            return null;
        }
        String expectedFileName = folderName + "_" + trajectoryFileName + FILE_EXTENSION_XLSX;
        Path fullPath = baseDirectory.resolve(folderName).resolve(expectedFileName);

        return Files.exists(fullPath) ? fullPath : null;
    }

    public static List<String> extractModulationParamTsFilesByAreaClusterRefKey(List<String> splitedTsFileNameList, AreaClusterRefKey areaClusterRefKey) {

        //example file name: MR_BP23_T2_2022_dsr_AFL_2026-2027_BE_Other Gas conventional old 2.csv.6401800f-8425-49d5-a42b-e89cb1e8a293.arrow
        //area : BE
        //cluster name: Other Gas conventional old 2
        String targetSegment = "_" + areaClusterRefKey.area() + "_" + (areaClusterRefKey.thermalClusterRef() != null ? areaClusterRefKey.thermalClusterRef().getName() : "null") + ".csv";
        String targetSegmentLower = targetSegment.toLowerCase();

        return splitedTsFileNameList.stream()
                .filter(fileName -> {
                    String fileNameLower = fileName.toLowerCase();
                    return fileNameLower.contains(targetSegmentLower);
                })
                .toList();
    }

    private static LinkedHashMap<String, List<ThermalCommonParameterEntity>> extractCommonParamsByClusterRef(List<TrajectoryEntity> parameterTrajectories) {
        log.info("Extracting common parameters by cluster ref from {} trajectories", parameterTrajectories.size());
        return parameterTrajectories.stream()
                .flatMap(t -> Optional.ofNullable(t.getThermalCommonParameters()).orElseGet(List::of).stream())
                .filter(common -> {
                    ThermalClusterRef ref = common.getThermalClusterRef();
                    if (ref == null) return false;
                    String namePemmdb = ref.getNamePemmdb();
                    return (namePemmdb == null || !"NA".equalsIgnoreCase(namePemmdb.trim())) && ref.getThermalTechnology() == null;
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
                        .map(cap -> Map.entry(new AreaClusterRefKey(cap.getArea(), cap.getThermalClusterRef()), cap)))
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
            ThermalEconomicEnerContentEntity economicTrajectory,
            TrajectoryEntity economicCostTrajectory
    ) {
        ThermalClusterGenerationDto.ThermalClusterGenerationDtoBuilder thermalClusterGenerationDtoBuilder = ThermalClusterGenerationDto.builder();

        buildFromClusterCapacity(thermalClusterCapacities, thermalClusterGenerationDtoBuilder);
        buildFromCommonParameters(thermalCommonParameters, thermalClusterGenerationDtoBuilder);
        buildFromSpecificParameters(thermalCommonParameters, thermalSpecificParameters, thermalClusterGenerationDtoBuilder);

        ThermalClusterGenerationDto dto = computeCostsForGenerationDto(thermalClusterCapacities, thermalSpecificParameters,
                economicTrajectory, economicCostTrajectory, thermalClusterGenerationDtoBuilder, thermalCommonParameters);

        return dto;
    }

    private ThermalClusterGenerationDto computeCostsForGenerationDto(List<ThermalClusterCapacityEntity> thermalClusterCapacities,
                                                                     List<ThermalSpecificParametersEntity> specificParams,
                                                                     ThermalEconomicEnerContentEntity economicTrajectory,
                                                                     TrajectoryEntity economicCostTrajectory,
                                                                     ThermalClusterGenerationDto.ThermalClusterGenerationDtoBuilder thermalClusterGenerationDtoBuilder,
                                                                     List<ThermalCommonParameterEntity> commonParams) {
        ThermalClusterGenerationDto dto = thermalClusterGenerationDtoBuilder.build();

        thermalCostAssembler.computeCo2(dto, commonParams, economicTrajectory, economicCostTrajectory);

        thermalCostAssembler.computeStartupAndMarginalCost(dto, commonParams, specificParams,
                thermalClusterCapacities, economicCostTrajectory);

        thermalCostAssembler.computeMarketBidCost(dto, commonParams, specificParams);
        return dto;
    }


    private void buildFromClusterCapacity(List<ThermalClusterCapacityEntity> thermalClusterCapacities, ThermalClusterGenerationDto.ThermalClusterGenerationDtoBuilder builder) {
        log.info("Building cluster properties from {} thermal capacities", thermalClusterCapacities.size());
        // max POWER capacity
        OptionalDouble maxPowerOpt = thermalClusterCapacities.stream()
                .filter(Objects::nonNull)
                .filter(cap -> cap.getCategory() == CategoryEnum.POWER)
                .map(ThermalClusterCapacityEntity::getValue)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max();

        // max unit count
        OptionalDouble unitCountOpt = thermalClusterCapacities.stream()
                .filter(Objects::nonNull)
                .filter(cap -> cap.getCategory() == CategoryEnum.NUMBER)
                .map(ThermalClusterCapacityEntity::getValue)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max();

        // nominal capacity (max POWER / unitCount) ---
        if (maxPowerOpt.isPresent()) {
            double maxPower = maxPowerOpt.getAsDouble();

            validatedUnitCountMax(thermalClusterCapacities, unitCountOpt);

            double nominalCapacity = maxPower / unitCountOpt.getAsDouble();
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

    private static void validatedUnitCountMax(List<ThermalClusterCapacityEntity> thermalClusterCapacities, OptionalDouble unitCountOpt) {
        if (unitCountOpt.isEmpty() || unitCountOpt.getAsDouble() == 0.0) {
            String clusterRefName = thermalClusterCapacities.stream()
                    .map(ThermalClusterCapacityEntity::getThermalClusterRef)
                    .filter(Objects::nonNull)
                    .map(ThermalClusterRef::getName)
                    .findFirst()
                    .orElse("unknown");
            String trajectoryName = thermalClusterCapacities.stream()
                    .map(ThermalClusterCapacityEntity::getTrajectory)
                    .filter(Objects::nonNull)
                    .map(TrajectoryEntity::getFileName)
                    .findFirst()
                    .orElse("unknown");
            throw BusinessException.builder()
                    .message("Failed to generate study. unit count must not be zero for thermal cluster: {0} in trajectory: {1}")
                    .errorMessageArguments(List.of(clusterRefName, trajectoryName))
                    .build();
        }
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

    private void buildFromSpecificParameters(List<ThermalCommonParameterEntity> thermalCommonParameters, List<ThermalSpecificParametersEntity> thermalSpecificParameters, ThermalClusterGenerationDto.ThermalClusterGenerationDtoBuilder builder) {

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
                .ifPresentOrElse(foDuration -> builder.foDuration(round(foDuration)),
                        () -> firstNonNullDouble(thermalCommonParameters, ThermalCommonParameterEntity::getFoDurationDefault)
                                .ifPresent(foDuration -> builder.foDuration(round(foDuration))));

        //PO duration
        thermalSpecificParameters.stream()
                .map(ThermalSpecificParametersEntity::getPoDuration)
                .filter(Objects::nonNull)
                .findFirst()
                .ifPresentOrElse(poDuration -> builder.poDuration(round(poDuration)),
                        () -> firstNonNullDouble(thermalCommonParameters, ThermalCommonParameterEntity::getPoDurationDefault)
                                .ifPresent(poDuration -> builder.poDuration(round(poDuration))));
        //FO Monthly rate
        thermalSpecificParameters.stream()
                .findFirst()
                .ifPresentOrElse(param -> {
                    if (param.getF1() != null) {
                        List<Double> forcedOutageMonthly = Arrays.asList(
                                round(param.getF1()), round(param.getF2()), round(param.getF3()), round(param.getF4()),
                                round(param.getF5()), round(param.getF6()), round(param.getF7()), round(param.getF8()),
                                round(param.getF9()), round(param.getF10()), round(param.getF11()), round(param.getF12())
                        );
                        builder.foMonthlyRate(forcedOutageMonthly);
                    } else {
                        firstNonNullDouble(thermalCommonParameters, ThermalCommonParameterEntity::getFoRateDefault)
                                .ifPresent(foRate -> builder.foMonthlyRate(Collections.nCopies(12, round(foRate))));
                    }
                }, () -> firstNonNullDouble(thermalCommonParameters, ThermalCommonParameterEntity::getFoRateDefault)
                        .ifPresent(foRate -> builder.foMonthlyRate(Collections.nCopies(12, round(foRate)))));

        //PO Monthly rate
        thermalSpecificParameters.stream()
                .findFirst()
                .ifPresentOrElse(param -> {
                    if (param.getP1() != null) {
                        List<Double> plannedOutageMonthly = Arrays.asList(
                                round(param.getP1()), round(param.getP2()), round(param.getP3()), round(param.getP4()),
                                round(param.getP5()), round(param.getP6()), round(param.getP7()), round(param.getP8()),
                                round(param.getP9()), round(param.getP10()), round(param.getP11()), round(param.getP12())
                        );
                        builder.poMonthlyRate(plannedOutageMonthly);
                    } else {
                        firstNonNullDouble(thermalCommonParameters, ThermalCommonParameterEntity::getPoWinterDefault)
                                .ifPresent(poRate -> builder.poMonthlyRate(Collections.nCopies(12, round(poRate))));
                    }
                }, () -> firstNonNullDouble(thermalCommonParameters, ThermalCommonParameterEntity::getPoWinterDefault)
                        .ifPresent(poRate -> builder.poMonthlyRate(Collections.nCopies(12, round(poRate)))));
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
