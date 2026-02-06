package com.rte_france.antares.datamanager_back.service.study.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.AreaDTO;
import com.rte_france.antares.datamanager_back.dto.StsGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.ThermalClusterGenerationDto;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.mapper.AreaMapper;
import com.rte_france.antares.datamanager_back.repository.LoadRepository;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.model.LinkEntity;
import com.rte_france.antares.datamanager_back.repository.model.LoadEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.sts.StsGenerationAssemblerService;
import com.rte_france.antares.datamanager_back.service.study.StudyGeneratorService;
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
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudyGeneratorServiceImpl implements StudyGeneratorService {

    private final NasFileService nasFileService;


    private final StudyRepository studyRepository;

    private final LoadRepository loadRepository;

    private final WebClient webClient;

    private final AntaressDataManagerProperties antaressDataManagerProperties;

    private final ThermalPropertiesAssemblerService thermalPropertiesAssemblerService;
    private final StsGenerationAssemblerService stPropertiesAssemblerService;

    private static final String PROPERTIES = "properties";
    private static final String DATA = "data";
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
            String studyJsonDir = antaressDataManagerProperties.getStudyJsonOutputDirectory();
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
                throw com.rte_france.antares.datamanager_back.exception.BusinessException.builder()
                        .message("No trajectories found for study id=" + studyId + "; cannot build areas/links")
                        .build();
            } else {
                for (TrajectoryEntity trajectory : trajectories) {
                    var trajectoryType = TrajectoryType.valueOf(trajectory.getType());
                    log.info("Processing trajectory fileName={} type={} area={}", trajectory.getFileName(), trajectory.getType(), trajectory.getArea());

                    switch (trajectoryType) {
                        case AREA -> buildAreasDataMap(study, trajectory, areasMap);
                        case LINK -> buildLinksDataMap(trajectory, linksMap);
                        case LOAD ->
                                log.warn("Load trajectory type is managed in AREA  trajectory: {}", trajectory.getFileName());
                        case THERMAL_CAPACITY, THERMAL_TECHNICAL_COMMON_PARAMETER, THERMAL_ECONOMIC_COST_PARAMETER,
                             THERMAL_ECONOMIC_PARAMETER,
                             THERMAL_TECHNICAL_SPECIFIC_PARAMETER, THERMAL_TECHNICAL_MODULATION_PARAMETER ->
                                log.warn("Thermal trajectories are managed in AREA  trajectory: {}", trajectory.getFileName());
                        case STS ->
                                log.warn("STS trajectories are managed in AREA  trajectory: {}", trajectory.getFileName());
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
            log.info("Construction terminée pour l'étude {} : {} areas, {} links", study.getName(), areasMap.size(), linksMap.size());
        } else {
            log.error("Study not found with ID: {}", studyId);
            throw TechnicalException.builder().message("Study not found with ID: " + studyId).build();
        }

        return jsonForGenerator;
    }


    public Map<String, List<String>> getListArrowLoadFilesByAreaFromStudy(StudyEntity studyEntity) {
        log.info("Récupération des fichiers LOAD par zone pour l'étude id={}", studyEntity.getId());
        Pattern pattern = Pattern.compile("_(.*?)[_\\.]");
        Map<String, List<String>> result = studyEntity.getTrajectories().stream()
                .filter(this::isLoadTrajectoryWithEntities)
                .flatMap(trajectory -> processTrajectoryLoads(trajectory, studyEntity.getId(), pattern))
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                ));
        log.info("Résultat LOAD files par zone: {} entrées", result.size());
        return result;
    }

    private boolean isLoadTrajectoryWithEntities(TrajectoryEntity trajectory) {
        return "LOAD".equals(trajectory.getType())
                && trajectory.getLoadEntities() != null
                && !trajectory.getLoadEntities().isEmpty();
    }

    private Stream<Map.Entry<String, String>> processTrajectoryLoads(TrajectoryEntity trajectory, Integer studyId, Pattern pattern) {
        log.info("Traitement des loads pour trajectory={} area={}", trajectory.getFileName(), trajectory.getArea());
        if ("OTHERS".equals(trajectory.getArea())) {
            return trajectory.getLoadEntities().stream()
                    .filter(loadEntity -> isLoadLinkedToStudy(loadEntity, studyId))
                    .map(loadEntity -> processLoadEntityWithPattern(loadEntity, trajectory, pattern));
        } else {
            return trajectory.getLoadEntities().stream()
                    .map(loadEntity -> {
                        processLoadEntityWithPattern(loadEntity, trajectory, pattern);
                        return Map.entry(trajectory.getArea().toUpperCase(), loadEntity.getOutPutFileName());
                    });

        }
    }

    private Map.Entry<String, String> processLoadEntityWithPattern(LoadEntity loadEntity, TrajectoryEntity trajectory, Pattern pattern) {
        if (loadEntity.getOutPutFileName() == null) {
            log.info("Aucun outPutFileName pour load {} - génération en cours", loadEntity.getFileName());
            String outputFileName = generateAndSaveOutputFileName(loadEntity, trajectory);
            loadEntity.setOutPutFileName(outputFileName);
            loadRepository.save(loadEntity);
            log.info("OutPutFileName généré et sauvegardé pour load {} : {}", loadEntity.getFileName(), outputFileName);
        } else {
            log.info("OutPutFileName existant pour load {} : {}", loadEntity.getFileName(), loadEntity.getOutPutFileName());
        }
        String area = extractAreaFromFileName(loadEntity.getOutPutFileName(), pattern);
        return Map.entry(area, loadEntity.getOutPutFileName());
    }

    private String generateAndSaveOutputFileName(LoadEntity loadEntity, TrajectoryEntity trajectory) {
        String outputLoadDir = antaressDataManagerProperties.getOutputLoadDirectory();
        var inputTxtFilePath = Paths.get(
                antaressDataManagerProperties.getNasDirectory(),
                antaressDataManagerProperties.getTrajectoryFilePath(),
                antaressDataManagerProperties.getLoadDirectory(),
                trajectory.getFileName(),
                loadEntity.getFileName()
        ).normalize();

        try {
            String saved = nasFileService.saveMatrixToNas(inputTxtFilePath, outputLoadDir);
            log.info("Matrix saved to NAS for input {} -> {}", inputTxtFilePath, saved);
            return saved;
        } catch (IOException e) {
            log.error("Erreur lors de la sauvegarde du matrix pour {} : {}", inputTxtFilePath, e.getMessage());
            throw TechnicalException.builder().message(e.getMessage()).cause(e).build();
        }
    }

    private String extractAreaFromFileName(String fileName, Pattern pattern) {
        var matcher = pattern.matcher(fileName);
        String area = matcher.find() ? matcher.group(1).toUpperCase() : "OTHERS";
        log.info("Extraction de la zone à partir du nom de fichier '{}': {}", fileName, area);
        return area;
    }

    private boolean isLoadLinkedToStudy(LoadEntity loadEntity, int studyId) {
        return loadEntity.getTrajectoryEntities().stream()
                .flatMap(traj -> traj.getScenarioEntities().stream())
                .anyMatch(study -> study.getId().equals(studyId));
    }

    private void buildAreasDataMap(StudyEntity studyEntity, TrajectoryEntity trajectory, Map<String, Object> areasMap) {
        log.info("Construction des areas data pour trajectory={} area={}", trajectory.getFileName(), trajectory.getArea());

        List<AreaDTO> areaDTOs = trajectory.getAreaConfigEntities().stream()
                .map(AreaMapper::toAreaDto)
                .toList();

        // Get LOAD files by area from all study trajectories
        Map<String, List<String>> listArrowLoadFilesByArea = getListArrowLoadFilesByAreaFromStudy(studyEntity);
        log.info("Nombre de zones LOAD trouvées: {}", listArrowLoadFilesByArea != null ? listArrowLoadFilesByArea.size() : 0);

        // Get thermal cluster generation DTOs for all trajectories in the study
        var areaClusterRefThermalClusterGenerationDtoMap = thermalPropertiesAssemblerService.assembleForTrajectories(studyEntity);
        log.info("Thermal cluster props pour l'étude: {} entrées", areaClusterRefThermalClusterGenerationDtoMap != null ? areaClusterRefThermalClusterGenerationDtoMap.size() : 0);

        var areaStsClusterGenerationDtoMap = stPropertiesAssemblerService.assembleStsProperties(studyEntity);
        log.info("STS cluster props pour l'étude: {} entrées", areaStsClusterGenerationDtoMap != null ? areaStsClusterGenerationDtoMap.size() : 0);

        Map<String, Map<String, Object>> areasDataMap = areaDTOs.stream()
                .collect(Collectors.toMap(
                        AreaDTO::getName,
                        areaDTO -> areasMapGenerator(
                                areaDTO,
                                listArrowLoadFilesByArea.get(areaDTO.getName()),
                                getClusterPropsForArea(areaClusterRefThermalClusterGenerationDtoMap, areaDTO.getName()),
                                areaStsClusterGenerationDtoMap
                        )
                ));

        areasMap.putAll(areasDataMap);
        log.info("Areas data map construite avec {} entrées", areasDataMap.size());
    }

    private static Map<String, ThermalClusterGenerationDto> getClusterPropsForArea(Map<ThermalPropertiesAssemblerService.AreaClusterRefKey, ThermalClusterGenerationDto> areaRefProps, String areaName) {
        return areaRefProps.entrySet().stream()
                .filter(e -> e.getKey().area().equalsIgnoreCase(areaName))
                .collect(Collectors.toMap(
                        e -> e.getKey().area().toUpperCase(Locale.ROOT) + "_" + e.getKey().thermalClusterRef().getName(),
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    private void buildLinksDataMap(TrajectoryEntity trajectory, Map<String, Object> linksMap) {
        log.info("Construction des links data pour trajectory={} liensCount={}", trajectory.getFileName(), trajectory.getLinkEntities() != null ? trajectory.getLinkEntities().size() : 0);
        List<LinkEntity> linkEntityList = trajectory.getLinkEntities();

        Map<String, Map<String, Object>> linksDataMap = linkEntityList.stream()
                .collect(Collectors.toMap(
                        linkEntity -> linkEntity.getName().replace("-", "/"),
                        linkEntity -> {
                            Map<String, Object> linkMap = linksMapGenerator();
                            linkMap.put("winterHpDirectMw", linkEntity.getWinterHpDirectMw());
                            linkMap.put("winterHpIndirectMw", linkEntity.getWinterHpIndirectMw());
                            linkMap.put("winterHcDirectMw", linkEntity.getWinterHcDirectMw());
                            linkMap.put("winterHcIndirectMw", linkEntity.getWinterHcIndirectMw());
                            linkMap.put("summerHpDirectMw", linkEntity.getSummerHpDirectMw());
                            linkMap.put("summerHpIndirectMw", linkEntity.getSummerHpIndirectMw());
                            linkMap.put("summerHcDirectMw", linkEntity.getSummerHcDirectMw());
                            linkMap.put("summerHcIndirectMw", linkEntity.getSummerHcIndirectMw());
                            linkMap.put("hurdleCost", linkEntity.getHurdleCost());
                            return linkMap;
                        },
                        (existing, replacement) -> existing
                ));

        linksMap.putAll(linksDataMap);
        log.info("Links data map construite avec {} entrées", linksDataMap.size());
    }

    /**
     * This method should be enriched or simplified when we'll have
     * all configurations for area from input files
     */
    private static Map<String, Object> areasMapGenerator(AreaDTO areaDTO, List<String> arrowLoadFilesByArea, Map<String, ThermalClusterGenerationDto> clusterProps, Map<String, StsGenerationDTO> stsClusterProps) {
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

        Map<String, Object> thermalsMap = thermalsMapGenerator(clusterProps);

        Map<String, Object> stsMap = stsMapGenerator(areaDTO.getName(), stsClusterProps);

        areaMap.put("hydro", hydroMap);
        areaMap.put("loads", arrowLoadFilesByArea != null && !arrowLoadFilesByArea.isEmpty() ? arrowLoadFilesByArea : "No LOAD files for this area");
        areaMap.put("thermals", thermalsMap);
        areaMap.put("sts", stsMap);

        return areaMap;
    }

    private static final ObjectMapper PROPERTIES_MAPPER = new ObjectMapper()
            .setConfig(new ObjectMapper().getSerializationConfig().withView(ThermalClusterGenerationDto.ThermalClusterViews.Properties.class));

    private static final ObjectMapper DATA_MAPPER = new ObjectMapper()
            .setConfig(new ObjectMapper().getSerializationConfig().withView(ThermalClusterGenerationDto.ThermalClusterViews.Data.class));

    private static final ObjectMapper PARAM_MODULATION_MAPPER = new ObjectMapper()
            .setConfig(new ObjectMapper().getSerializationConfig().withView(ThermalClusterGenerationDto.ThermalClusterViews.ParamModulation.class));

    private static Map<String, Object> thermalsMapGenerator(Map<String, ThermalClusterGenerationDto> clusterProps) {
        if (clusterProps == null || clusterProps.isEmpty()) {
            log.info("thermalsMapGenerator: pas de clusterProps fournies");
            return Collections.emptyMap();
        }

        Map<String, Object> clusterMap = new LinkedHashMap<>();

        clusterProps.forEach((clusterName, dto) -> {

            Map<String, Object> propertiesMap = PROPERTIES_MAPPER.convertValue(dto, new TypeReference<>() {
            });

            Map<String, Object> dataMap = DATA_MAPPER.convertValue(dto, new TypeReference<>() {
            });

            Map<String, Object> paramModulation = PARAM_MODULATION_MAPPER.convertValue(dto, new TypeReference<>() {
            });

            Map<String, Object> clusterData = new LinkedHashMap<>();
            clusterData.put(PROPERTIES, propertiesMap);
            clusterData.put("series", MATRIX_HASH);
            clusterData.put("fuel_cost", MATRIX_HASH);
            clusterData.put("co2_cost", MATRIX_HASH);
            clusterData.put(DATA, dataMap);
            clusterData.put("modulation", dto.getParamModulationTsList());

            clusterMap.put(clusterName, clusterData);
            log.info("Ajout thermal cluster {} avec {} propriétés", clusterName, propertiesMap.size());
        });

        log.info("thermalsMapGenerator: {} clusters générés", clusterMap.size());
        return clusterMap;
    }


    private static Map<String, Object> stsMapGenerator(String areaName, Map<String, StsGenerationDTO> stsClusterProps) {
        if (stsClusterProps == null || stsClusterProps.isEmpty()) {
            log.info("stsMapGenerator: pas de stsClusterProps pour area={}", areaName);
            return Collections.emptyMap();
        }

        Map<String, Object> stsClusterName = new LinkedHashMap<>();

        stsClusterProps.entrySet().stream()
                .filter(e -> e.getKey().startsWith(areaName.toUpperCase() + "_"))
                .forEach(e -> {
                    String clusterName = e.getKey();
                    StsGenerationDTO dto = e.getValue();
                    Map<String, Object> propertiesMap = new LinkedHashMap<>();
                    propertiesMap.put("enabled", dto.getEnabled());
                    propertiesMap.put("group", dto.getGroupe());
                    propertiesMap.put("injection_nominal_capacity", dto.getInjection());
                    propertiesMap.put("withdrawal_nominal_capacity", dto.getWithdrawal());
                    propertiesMap.put("reservoir_capacity", dto.getStorage());
                    propertiesMap.put("efficiency", dto.getEfficiencyInjection());
                    propertiesMap.put("efficiency_withdrawal", dto.getEfficiencyWithdrawal());
                    propertiesMap.put("initial_level", dto.getInitialLevel());
                    propertiesMap.put("initial_level_optim", dto.getInitialLevelOptim());

                    Map<String, Object> clusterData = new LinkedHashMap<>();
                    clusterData.put(PROPERTIES, propertiesMap);
                    clusterData.put("series", dto.getStsTsList());

                    stsClusterName.put(clusterName, clusterData);
                    log.info("Ajout STS cluster {} pour area {} (enabled={})", clusterName, areaName, dto.getEnabled());
                });

        log.info("stsMapGenerator: {} clusters STS générés pour area {}", stsClusterName.size(), areaName);
        return stsClusterName;
    }


    private static Map<String, Object> linksMapGenerator() {
        Map<String, Object> linkMap = new HashMap<>();
        linkMap.put(PROPERTIES, "LinkProperties as JSON");
        linkMap.put("ui", "LinkUi class as JSON");
        linkMap.put("parameters", MATRIX_HASH);
        linkMap.put("capacity_direct", MATRIX_HASH);
        linkMap.put("capacity_indirect", MATRIX_HASH);

        return linkMap;
    }

    @ExecutionTime
    public void callGenerateStudyService(Integer studyId) {
        log.info("Appel du service de génération pour l'étude id={}", studyId);
        String url = antaressDataManagerProperties.getGeneratorHostUrl() + "/generate_study/?study_id=" + studyId;

        try {
            webClient.post()
                    .uri(url)
                    .exchangeToMono(resp -> {
                        if (resp.statusCode().equals(HttpStatus.OK)) {
                            log.info("Study {} has been successfully generated", studyId);
                            return resp.bodyToMono(String.class);
                        } else {
                            log.error("Error while generating study {{}}", studyId);
                            return resp.createException().flatMap(Mono::error);
                        }

                    })
                    .block();
        } catch (RuntimeException ex) {
            log.error("Erreur lors de l'appel au générateur pour l'étude {} : {}", studyId, ex.getMessage());
            throw TechnicalException.builder()
                    .message("Error while call Generate study from generator " + studyId + ": " + ex.getMessage())
                    .cause(ex.getCause())
                    .build();
        }
    }
}
