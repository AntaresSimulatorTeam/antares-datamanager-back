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
import com.rte_france.antares.datamanager_back.service.misc.MiscGenerationAssemblerService;
import com.rte_france.antares.datamanager_back.service.res.ResGenerationAssemblerService;
import com.rte_france.antares.datamanager_back.service.sts.StsGenerationAssemblerService;
import com.rte_france.antares.datamanager_back.service.study.*;
import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalPropertiesAssemblerService;
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
    private final StudyRepository studyRepository;

    private final WebClient webClient;

    private final AntaresDataManagerProperties antaresDataManagerProperties;

    private final ThermalPropertiesAssemblerService thermalPropertiesAssemblerService;
    private final StsGenerationAssemblerService stPropertiesAssemblerService;
    private final DsrGenerationAssemblerService dsrPropertiesAssemblerService;
    private final MiscGenerationAssemblerService miscPropertiesAssemblerService;
    private final ResGenerationAssemblerService resGenerationAssemblerService;

    private static final String PROPERTIES = "properties";
    private static final String MATRIX_HASH = "matrix hash";


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

    private Map<String, Object> buildJsonStudyDataForGeneration(Integer studyId) {
        log.info("Construction des données JSON pour génération - étude id={}", studyId);
        Map<String, Object> jsonForGenerator = new TreeMap<>();

        Optional<StudyEntity> studyEntity = studyRepository.findById(studyId);

        if (studyEntity.isPresent()) {
            StudyEntity study = studyEntity.get();
            Set<TrajectoryEntity> trajectories = study.getTrajectories();
            log.info("Study found id={} name={} with {} trajectories", studyId, study.getName(), trajectories != null ? trajectories.size() : 0);

            Map<String, Object> areasMap = new TreeMap<>();
            Map<String, Object> linksMap = new TreeMap<>();

            if (trajectories == null || trajectories.isEmpty()) {
                throw BusinessException.builder()
                        .message("No trajectories found for study id=" + studyId + "; cannot build areas/links")
                        .build();
            } else {
                for (TrajectoryEntity trajectory : trajectories) {
                    var trajectoryType = TrajectoryType.valueOf(trajectory.getType());
                    log.info("Processing trajectory fileName={} type={} area={}", trajectory.getFileName(), trajectory.getType(), trajectory.getArea());

                    switch (trajectoryType) {
                        case AREA -> buildAreasDataMap(study, trajectory, areasMap);
                        case LINK -> linksToJsonService.buildLinksDataMap(trajectory, linksMap);
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
                        default -> {
                            log.error("Unhandled trajectory type {} for trajectory {}", trajectoryType, trajectory.getFileName());
                            throw TechnicalException.builder().message("Unhandled trajectory for generation: " + trajectoryType).build();
                        }
                    }
                }
            }

            Map<String, Object> innerGeneratorMap = new TreeMap<>();
            innerGeneratorMap.put("version", "9.3");
            innerGeneratorMap.put("settings", "will be refactored so we'll put nothing for the moment");
            // TODO: get input for random generation flag and number of years, maybe also move them somewhere else
            innerGeneratorMap.put("enable_random_ts", true);
            innerGeneratorMap.put("nb_years", 1);
            innerGeneratorMap.put("areas", areasMap);
            innerGeneratorMap.put("links", linksMap);

            jsonForGenerator.put(study.getName(), innerGeneratorMap);
            log.info("Generation JSON assembled for study {} : {} areas, {} links", study.getName(), areasMap.size(), linksMap.size());
        } else {
            log.error("Study not found with ID: {}", studyId);
            throw TechnicalException.builder().message("Study not found with ID: " + studyId).build();
        }

        return jsonForGenerator;
    }


    private void buildAreasDataMap(StudyEntity studyEntity, TrajectoryEntity trajectory, Map<String, Object> areasMap) {
        log.info("Construction des areas data pour trajectory={} area={}", trajectory.getFileName(), trajectory.getArea());

        List<AreaDTO> areaDTOs = trajectory.getAreaConfigEntities().stream()
                .map(AreaMapper::toAreaDto)
                .toList();

        // Get LOAD files by area from all study trajectories
        Map<String, List<String>> listArrowLoadFilesByArea = loadToJsonService.getListArrowLoadFilesByAreaFromStudy(studyEntity);
        log.info("Number of zones LOAD found: {}", listArrowLoadFilesByArea != null ? listArrowLoadFilesByArea.size() : 0);

        // Get thermal cluster generation DTOs for all trajectories in the study
        var areaClusterRefThermalClusterGenerationDtoMap = thermalPropertiesAssemblerService.assembleForTrajectories(studyEntity);
        log.info("Thermal cluster props found: {}", areaClusterRefThermalClusterGenerationDtoMap != null ? areaClusterRefThermalClusterGenerationDtoMap.size() : 0);

        var areaStsClusterGenerationDtoMap = stPropertiesAssemblerService.assembleStsProperties(studyEntity);
        log.info("STS cluster props {} entries", areaStsClusterGenerationDtoMap != null ? areaStsClusterGenerationDtoMap.size() : 0);

        var areaDsrClusterGenerationDtoMap = dsrPropertiesAssemblerService.assembleDsrProperties(studyEntity);
        log.info("DSR cluster props {} entries", areaDsrClusterGenerationDtoMap != null ? areaDsrClusterGenerationDtoMap.size() : 0);

        Map<String, List<MiscGenerationDTO>> areaMiscGenerationDtoMap = miscPropertiesAssemblerService.assembleMiscProperties(studyEntity);
        log.info("Misc generation {} entries", areaMiscGenerationDtoMap != null ? areaMiscGenerationDtoMap.size() : 0);

        Map<String, Map<String, Object>> areaResGenerationMap = resGenerationAssemblerService.assembleResProperties(studyEntity);
        log.info("RES generation {} entries", areaResGenerationMap != null ? areaResGenerationMap.size() : 0);

        Map<String, Map<String, Object>> areasDataMap = areaDTOs.stream()
                .collect(Collectors.toMap(
                        AreaDTO::getName,
                        areaDTO -> areasMapGenerator(
                                areaDTO,
                                listArrowLoadFilesByArea.get(areaDTO.getName()),
                                thermalToJsonService.getClusterPropsForArea(areaClusterRefThermalClusterGenerationDtoMap, areaDTO.getName()),
                                areaStsClusterGenerationDtoMap,
                                areaDsrClusterGenerationDtoMap,
                                areaMiscGenerationDtoMap,
                                areaResGenerationMap

                        )
                ));

        areasMap.putAll(areasDataMap);
        log.info("Areas data with {} entries", areasDataMap.size());
    }

    private Map<String, Object> areasMapGenerator(AreaDTO areaDTO, List<String> arrowLoadFilesByArea, Map<String, ThermalClusterGenerationDto> clusterProps,
                                                  Map<String, StsGenerationDTO> stsClusterProps,
                                                  Map<String, DsrGenerationDTO> dsrClusterProps,
                                                  Map<String, List<MiscGenerationDTO>> miscProps,
                                                  Map<String, Map<String, Object>> resProps
    ) {
        log.info("areasMapGenerator invoked for area={}", areaDTO.getName());
        // This is a placeholder for the actual AreaUI and AreaProperties classes
        // Replace with actual implementations or JSON representations
        Map<String, Object> areaMap = new HashMap<>();
        areaMap.put("ui", "AreaUI class as JSON");

        Map<String, Object> areaProperties = new HashMap<>();
        areaProperties.put("energy_cost_unsupplied", areaDTO.getUnsuppliedEnergyCost());
        areaProperties.put("energy_cost_spilled", areaDTO.getSpilledEnergyCost());
        areaMap.put(PROPERTIES, areaProperties);

        Map<String, Object> hydroMap = new HashMap<>();
        hydroMap.put(PROPERTIES, "HydroProperties as JSON");
        hydroMap.put("every matrices name inside HydroMatrixName enum", MATRIX_HASH);

        Map<String, Object> thermalsMap = thermalToJsonService.thermalsMapGenerator(clusterProps);

        Map<String, Object> stsMap = stsToJsonService.stsMapGenerator(areaDTO.getName(), stsClusterProps);
        Map<String, Object> dsrMap = dsrToJsonService.buildDsrDataMap(areaDTO.getName(), dsrClusterProps);
        Map<String, Object> miscMap = miscToJsonService.buildMiscDataMap(areaDTO.getName(), miscProps);
        Map<String, Object> resMap = resToJsonService.buildResDataMap(areaDTO.getName(), resProps);

        areaMap.put("hydro", hydroMap);
        areaMap.put("loads", arrowLoadFilesByArea != null && !arrowLoadFilesByArea.isEmpty() ? arrowLoadFilesByArea : "No LOAD files for this area");
        areaMap.put("thermals", thermalsMap);
        areaMap.put("sts", stsMap);
        areaMap.put("dsr", dsrMap);
        areaMap.put("misc", miscMap);
        areaMap.put("res", resMap);

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
