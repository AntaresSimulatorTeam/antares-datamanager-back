package com.rte_france.antares.datamanager_back.service.study.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.*;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.mapper.AreaMapper;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.dsr.DsrGenerationAssemblerService;
import com.rte_france.antares.datamanager_back.service.nuclear.NuclearBindingConstraintAssemblerService;
import com.rte_france.antares.datamanager_back.service.nuclear.NuclearClusterNames;
import com.rte_france.antares.datamanager_back.service.hydro.HydroGenerationAssemblerService;
import com.rte_france.antares.datamanager_back.service.misc.MiscGenerationAssemblerService;
import com.rte_france.antares.datamanager_back.service.res.ResGenerationAssemblerService;
import com.rte_france.antares.datamanager_back.service.sts.StsGenerationAssemblerService;
import com.rte_france.antares.datamanager_back.service.study.*;
import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalPropertiesAssemblerService;
import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalPropertiesAssemblerService.AreaClusterRefKey;
import com.rte_france.antares.datamanager_back.util.ExecutionTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class StudyGeneratorServiceImpl implements StudyGeneratorService {

    private final NasFileService nasFileService;

    private final LoadToJsonService loadToJsonService;
    private final LinksToJsonService linksToJsonService;
    private final StsToJsonService stsToJsonService;
    private final DsrToJsonService dsrToJsonService;
    private final MiscToJsonService miscToJsonService;
    private final ResToJsonService resToJsonService;
    private final ThermalToJsonService thermalToJsonService;
    private final HydroToJsonService hydroToJsonService;
    private final StudyRepository studyRepository;

    private final WebClient webClient;

    private final AntaresDataManagerProperties antaresDataManagerProperties;

    private final ThermalPropertiesAssemblerService thermalPropertiesAssemblerService;
    private final StsGenerationAssemblerService stPropertiesAssemblerService;
    private final DsrGenerationAssemblerService dsrPropertiesAssemblerService;
    private final MiscGenerationAssemblerService miscPropertiesAssemblerService;
    private final ResGenerationAssemblerService resGenerationAssemblerService;
    private final HydroGenerationAssemblerService hydroGenerationAssemblerService;
    private final NuclearBindingConstraintAssemblerService nuclearBindingConstraintAssemblerService;

    private static final String PROPERTIES = "properties";


    @ExecutionTime
    @Override
    public void buildJsonForStudyGeneration(Integer studyId) throws TechnicalException {
        String sid = studyId != null ? String.valueOf(studyId) : "null";
        MDC.put("studyId", sid);
        log.info("Début de la génération du JSON pour l'étude id={}", studyId);
        Map<String, Object> jsonStudyDataForGeneration = buildJsonStudyDataForGeneration(studyId);
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String generatorJson = objectMapper.writeValueAsString(jsonStudyDataForGeneration);
            String studyJsonDir = antaresDataManagerProperties.getStudyJsonOutputDirectory();
            log.info("Sauvegarde du JSON de génération pour l'étude {} dans {}", studyId, studyJsonDir);
            nasFileService.saveFile(studyId + ".json", generatorJson.getBytes(), studyJsonDir);
            log.info("JSON pour l'étude {} sauvegardé avec succès", studyId);
        } catch (IOException e) {
            log.error("Erreur lors de la génération/sauvegarde du JSON pour l'étude {} : {}", studyId, e.getMessage());
            throw TechnicalException.builder()
                    .message("Erreur lors de la génération du fichier JSON : " + e)
                    .cause(e)
                    .build();
        } finally {
            MDC.remove("studyId");
            log.debug("MDC studyId removed for studyId={}", sid);
        }
    }

    private Map<String, Object> buildJsonStudyDataForGeneration(Integer studyId) throws BusinessException, TechnicalException {
        log.info("Construction des données JSON pour génération - étude id={}", studyId);

        Optional<StudyEntity> studyEntity = studyRepository.findById(studyId);
        if (studyEntity.isEmpty()) {
            log.error("Study not found with ID: {}", studyId);
            throw TechnicalException.builder().message("Study not found with ID: " + studyId).build();
        }

        StudyEntity study = studyEntity.get();
        Set<TrajectoryEntity> trajectories = study.getTrajectories();
        log.info("Study found id={} name={} with {} trajectories", studyId, study.getName(), trajectories != null ? trajectories.size() : 0);

        if (trajectories == null || trajectories.isEmpty()) {
            throw BusinessException.builder()
                    .message("No trajectories found for study id=" + studyId + "; cannot build areas/links")
                    .build();
        }

        // Get thermal cluster generation DTOs for all trajectories in the study
        var thermalClusterProps = thermalPropertiesAssemblerService.assembleForTrajectories(study);
        TrajectoryDispatchResult dispatchResult = dispatchTrajectories(study, trajectories, thermalClusterProps);

        Map<String, Object> innerGeneratorMap = buildInnerGeneratorMap(dispatchResult, thermalClusterProps);

        Map<String, Object> jsonForGenerator = new TreeMap<>();
        jsonForGenerator.put(study.getName(), innerGeneratorMap);
        log.info("Generation JSON assembled for study {} : {} areas, {} links", study.getName(),
                dispatchResult.areasMap().size(), dispatchResult.linksMap().size());

        return jsonForGenerator;
    }

    private record TrajectoryDispatchResult(Map<String, Object> areasMap, Map<String, Object> linksMap,
                                             Optional<TrajectoryEntity> nuclearModulationTrajectory) {}

    private TrajectoryDispatchResult dispatchTrajectories(StudyEntity study, Set<TrajectoryEntity> trajectories,
                                                           Map<AreaClusterRefKey, ThermalClusterGenerationDto> thermalClusterProps) {
        Map<String, Object> areasMap = new TreeMap<>();
        Map<String, Object> linksMap = new TreeMap<>();
        Optional<TrajectoryEntity> nuclearModulationTraj = Optional.empty();

        for (TrajectoryEntity trajectory : trajectories) {
            var trajectoryType = TrajectoryType.valueOf(trajectory.getType());
            log.info("Processing trajectory fileName={} type={} area={}", trajectory.getFileName(), trajectory.getType(), trajectory.getArea());

            switch (trajectoryType) {
                case AREA -> buildAreasDataMap(study, trajectory, areasMap, thermalClusterProps);
                case LINK -> linksToJsonService.buildLinksDataMap(trajectory, linksMap, study);
                case LOAD ->
                        log.warn("Load trajectory type is managed in AREA  trajectory: {}", trajectory.getFileName());
                case THERMAL_CAPACITY, THERMAL_TECHNICAL_COMMON_PARAMETER, THERMAL_ECONOMIC_COST_PARAMETER,
                     THERMAL_ECONOMIC_PARAMETER,
                     THERMAL_TECHNICAL_SPECIFIC_PARAMETER, THERMAL_TECHNICAL_MODULATION_PARAMETER ->
                        log.warn("Thermal trajectories are managed in AREA  trajectory: {}", trajectory.getFileName());
                case STS ->
                        log.warn("STS trajectories are managed in AREA  trajectory: {}", trajectory.getFileName());
                case DSR, DSR_CAPACITY_MODULATION ->
                        log.warn("DSR trajectories are managed in AREA  trajectory: {}", trajectory.getFileName());
                case MISC_CAPACITY, MISC_LOAD ->
                        log.warn("MISC trajectories are managed in AREA  trajectory: {}", trajectory.getFileName());
                case RES_CAPACITY, RES_LOAD, RES_ZONAL_DISTRIBUTION, RES_TECHNOLOGY_DISTRIBUTION ->
                        log.warn("RES trajectories are managed in AREA trajectory: {}", trajectory.getFileName());
                case HYDRO_TECHNICAL_PARAMETERS, HYDRO_SERIES, HYDRO_PARAMETERS, HYDRO_ALLOCATION ->
                        log.warn("HYDRO trajectories are managed in AREA trajectory: {}", trajectory.getFileName());
                case HYDRO_PSP_SERIES, HYDRO_PSP_TECHNICAL_PARAMETERS ->
                        log.warn("HYDRO PSP trajectories are managed in AREA trajectory: {}", trajectory.getFileName());
                case NUCLEAR_FR_MODULATION -> nuclearModulationTraj = Optional.of(trajectory);
                case NUCLEAR_FR_TALON, NUCLEAR_FR_TS_ERP, NUCLEAR_FR_TS_LONG_TERM, NUCLEAR_FR_TS_SMR ->
                        log.warn("NUCLEAR trajectory assembled separately: {}", trajectory.getFileName());
                default -> {
                    log.error("Unhandled trajectory type {} for trajectory {}", trajectoryType, trajectory.getFileName());
                    throw TechnicalException.builder().message("Unhandled trajectory for generation: " + trajectoryType).build();
                }
            }
        }

        return new TrajectoryDispatchResult(areasMap, linksMap, nuclearModulationTraj);
    }

    private Map<String, Object> buildInnerGeneratorMap(TrajectoryDispatchResult dispatchResult,
                                                         Map<AreaClusterRefKey, ThermalClusterGenerationDto> thermalClusterProps) {
        Map<String, Object> areasMap = dispatchResult.areasMap();
        Map<String, Object> innerGeneratorMap = new TreeMap<>();
        innerGeneratorMap.put("version", "9.3");
        innerGeneratorMap.put("settings", "will be refactored so we'll put nothing for the moment");
        // TODO: get input for random generation flag and number of years, maybe also move them somewhere else
        innerGeneratorMap.put("enable_random_ts", true);
        innerGeneratorMap.put("nb_years", 1);
        innerGeneratorMap.put("areas", areasMap);
        innerGeneratorMap.put("links", dispatchResult.linksMap());
        dispatchResult.nuclearModulationTrajectory().ifPresent(traj -> {
            innerGeneratorMap.put("nuclear_binding_constraints",
                    nuclearBindingConstraintAssemblerService.assembleBindingConstraints(
                            traj, extractFrNuclearClusterNames(thermalClusterProps)));
            areasMap.put("y_nuc_modulation", buildYNucModulationAreaMap(thermalClusterProps));
        });
        return innerGeneratorMap;
    }


    private void buildAreasDataMap(StudyEntity studyEntity, TrajectoryEntity trajectory, Map<String, Object> areasMap,
                                   Map<AreaClusterRefKey, ThermalClusterGenerationDto> thermalClusterProps) throws BusinessException {
        log.info("Construction des areas data pour trajectory={} area={}", trajectory.getFileName(), trajectory.getArea());

        List<AreaDTO> areaDTOs = trajectory.getAreaConfigEntities().stream()
                .map(AreaMapper::toAreaDto)
                .toList();

        // Get LOAD files by area from all study trajectories
        Map<String, List<String>> listArrowLoadFilesByArea = loadToJsonService.getListArrowLoadFilesByAreaFromStudy(studyEntity);
        log.info("Number of zones LOAD found: {}", listArrowLoadFilesByArea != null ? listArrowLoadFilesByArea.size() : 0);
        
        log.info("Thermal cluster props found: {}", thermalClusterProps != null ? thermalClusterProps.size() : 0);

        var areaStsClusterGenerationDtoMap = stPropertiesAssemblerService.assembleStsProperties(studyEntity);
        log.info("STS cluster props {} entries", areaStsClusterGenerationDtoMap != null ? areaStsClusterGenerationDtoMap.size() : 0);

        var areaDsrClusterGenerationDtoMap = dsrPropertiesAssemblerService.assembleDsrProperties(studyEntity);
        log.info("DSR cluster props {} entries", areaDsrClusterGenerationDtoMap != null ? areaDsrClusterGenerationDtoMap.size() : 0);

        Map<String, List<MiscGenerationDTO>> areaMiscGenerationDtoMap = miscPropertiesAssemblerService.assembleMiscProperties(studyEntity);
        log.info("Misc generation {} entries", areaMiscGenerationDtoMap != null ? areaMiscGenerationDtoMap.size() : 0);

        var areaResGenerationMap = resGenerationAssemblerService.assembleResProperties(studyEntity);
        log.info("RES generation {} entries", areaResGenerationMap != null ? areaResGenerationMap.size() : 0);

        Map<String, HydroAreaGenerationDTO> areaHydroGenerationMap = hydroGenerationAssemblerService.assembleHydroProperties(studyEntity);
        log.info("HYDRO generation {} entries", areaHydroGenerationMap != null ? areaHydroGenerationMap.size() : 0);


        AreasGenerationContextDTO context = AreasGenerationContextDTO.builder()
                .arrowLoadFilesByArea(listArrowLoadFilesByArea)
                .clusterPropsByArea(Optional.ofNullable(thermalClusterProps)
                        .orElse(Collections.emptyMap())
                        .entrySet()
                        .stream()
                        .collect(Collectors.groupingBy(
                                e -> e.getKey().area(),
                                Collectors.toMap(
                                        e -> e.getKey().area().toUpperCase(Locale.ROOT) + "_" + e.getKey().thermalClusterRef().getName(),
                                        Map.Entry::getValue,
                                        (a, b) -> a,
                                        LinkedHashMap::new
                                )
                        )))
                .stsClusterProps(areaStsClusterGenerationDtoMap)
                .dsrClusterProps(areaDsrClusterGenerationDtoMap)
                .miscProps(areaMiscGenerationDtoMap)
                .resProps(areaResGenerationMap)
                .hydroProps(areaHydroGenerationMap)
                .build();

        Map<String, Map<String, Object>> areasDataMap = areaDTOs.stream()
                .collect(Collectors.toMap(
                        AreaDTO::getName,
                        areaDTO -> areasMapGenerator(areaDTO, context)
                ));

        areasMap.putAll(areasDataMap);
        log.info("Areas data with {} entries", areasDataMap.size());
    }

    private List<String> extractFrNuclearClusterNames(Map<AreaClusterRefKey, ThermalClusterGenerationDto> thermalClusterProps) {
        return thermalClusterProps.keySet().stream()
                .filter(key -> "fr".equalsIgnoreCase(key.area())
                        && NuclearClusterNames.isNuclear(key.thermalClusterRef().getName()))
                .map(key -> key.thermalClusterRef().getName())
                .distinct()
                .toList();
    }

    private Map<String, Object> buildYNucModulationAreaMap(Map<AreaClusterRefKey, ThermalClusterGenerationDto> thermalClusterProps) {
        Map<String, ThermalClusterGenerationDto> nonPeakNuclearClusters = thermalClusterProps.entrySet().stream()
                .filter(e -> {
                    String name = e.getKey().thermalClusterRef().getName();
                    return "fr".equalsIgnoreCase(e.getKey().area())
                            && NuclearClusterNames.isNuclear(name) && !NuclearClusterNames.isPeak(name);
                })
                .collect(Collectors.toMap(
                        e -> "y_nuc_modulation_" + e.getKey().thermalClusterRef().getName().toLowerCase(Locale.ROOT),
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        Map<String, Object> areaMap = new LinkedHashMap<>();
        areaMap.put("nuclear", Map.of("clusters", thermalToJsonService.thermalsMapGenerator(nonPeakNuclearClusters)));
        return areaMap;
    }

    private Map<String, Object> areasMapGenerator(AreaDTO areaDTO, AreasGenerationContextDTO context) {
        log.info("areasMapGenerator invoked for area={}", areaDTO.getName());
        // This is a placeholder for the actual AreaUI and AreaProperties classes
        // Replace with actual implementations or JSON representations
        Map<String, Object> areaMap = new HashMap<>();
        areaMap.put("ui", "AreaUI class as JSON");

        Map<String, Object> areaProperties = new HashMap<>();
        areaProperties.put("energy_cost_unsupplied", areaDTO.getUnsuppliedEnergyCost());
        areaProperties.put("energy_cost_spilled", areaDTO.getSpilledEnergyCost());
        areaMap.put(PROPERTIES, areaProperties);

        Map<String, ThermalClusterGenerationDto> allClusters = context.getClusterPropsByArea().get(areaDTO.getName());
        Map<String, ThermalClusterGenerationDto> nonNuclearClusters = new LinkedHashMap<>();
        Map<String, ThermalClusterGenerationDto> nuclearClusters = new LinkedHashMap<>();
        if (allClusters != null) {
            allClusters.forEach((key, dto) -> {
                if (NuclearClusterNames.isNuclear(key)) {
                    nuclearClusters.put(key, dto);
                } else {
                    nonNuclearClusters.put(key, dto);
                }
            });
        }

        Map<String, Object> thermalsMap = thermalToJsonService.thermalsMapGenerator(nonNuclearClusters);
        Map<String, Object> nuclearClustersMap = thermalToJsonService.thermalsMapGenerator(nuclearClusters);
        Map<String, Object> stsMap = stsToJsonService.stsMapGenerator(areaDTO.getName(), context.getStsClusterProps());
        Map<String, Object> dsrMap = dsrToJsonService.buildDsrDataMap(areaDTO.getName(), context.getDsrClusterProps());
        Map<String, Object> miscMap = miscToJsonService.buildMiscDataMap(areaDTO.getName(), context.getMiscProps());
        var resMap = resToJsonService.buildResDataMap(areaDTO.getName(), context.getResProps());
        Map<String, Object> hydroMap = hydroToJsonService.buildHydroDataMap(areaDTO.getName(), context.getHydroProps());

        List<String> arrowLoadFiles = context.getArrowLoadFilesByArea().get(areaDTO.getName());
        areaMap.put("loads", arrowLoadFiles != null && !arrowLoadFiles.isEmpty() ? arrowLoadFiles : "No LOAD files for this area");
        areaMap.put("thermals", thermalsMap);
        areaMap.put("nuclear", Map.of("clusters", nuclearClustersMap));
        areaMap.put("sts", stsMap);
        areaMap.put("dsr", dsrMap);
        areaMap.put("misc", miscMap);
        areaMap.put("res", resMap);
        areaMap.put("hydro", hydroMap);

        return areaMap;
    }


    @ExecutionTime
    public void callGenerateStudyService(Integer studyId) {
        log.info("Appel du service de génération pour l'étude id={}", studyId);
        String url = antaresDataManagerProperties.getGeneratorHostUrl() + "/generate_study/?study_id=" + studyId;

        try {
            webClient.post()
                    .uri(url)
                    .exchangeToMono(resp -> {
                        if (resp.statusCode().equals(HttpStatus.OK)) {
                            log.info("Study {} has been successfully generated", studyId);
                            return resp.bodyToMono(String.class);
                        } else {
                            return resp.bodyToMono(String.class)
                                    .defaultIfEmpty("(empty response body)")
                                    .flatMap(body -> {

                                        String msg = String.format("Error while generating study %d: %s", studyId, body);
                                        //remove ({\"detail\":\"Internal Error:) and (\"}) from msg
                                        msg = msg.replaceAll("\\{\"detail\":\"Internal Error: ?", "")
                                                .replaceAll("\"}", "")
                                                .trim();
                                        log.error(msg);
                                        return Mono.error(TechnicalException.builder().message(msg).build());
                                    });
                        }
                    })
                    .block();
        } catch (TechnicalException te) {
            throw te;
        } catch (RuntimeException ex) {
            log.error("Erreur lors de l'appel au générateur pour l'étude {} : {}", studyId, ex.getMessage());
            throw TechnicalException.builder()
                    .message("Error while call Generate study from generator " + studyId + ": " + ex.getMessage())
                    .cause(ex)
                    .build();
        }
    }

}
