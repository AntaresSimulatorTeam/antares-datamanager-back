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
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.sts.StsPropertiesAssemblerService;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.study.StudyGeneratorService;
import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalPropertiesAssemblerService;
import com.rte_france.antares.datamanager_back.util.ExecutionTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final StsPropertiesAssemblerService stPropertiesAssemblerService;

    private static final String PROPERTIES = "properties";
    private static final String DATA = "data";
    private static final String PARAM_MODULATION = "modulation";

    private static final String MATRIX_HASH = "matrix hash";


    @ExecutionTime
    @Override
    public void buildJsonForStudyGeneration(Integer studyId) throws TechnicalException {
        Map<String, Object> jsonStudyDataForGeneration = buildJsonStudyDataForGeneration(studyId);
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String generatorJson = objectMapper.writeValueAsString(jsonStudyDataForGeneration);
            String studyJsonDir = antaressDataManagerProperties.getStudyJsonOutputDirectory();
            nasFileService.saveFile(studyId + ".json", generatorJson.getBytes(), studyJsonDir);
        } catch (IOException e) {
            throw TechnicalException.builder()
                    .message("Erreur lors de la génération du fichier JSON : " + e)
                    .cause(e)
                    .build();
        }
    }

    private Map<String, Object> buildJsonStudyDataForGeneration(Integer studyId) {
        Map<String, Object> jsonForGenerator = new TreeMap<>();

        Optional<StudyEntity> studyEntity = studyRepository.findById(studyId);

        if (studyEntity.isPresent()) {
            StudyEntity study = studyEntity.get();
            Set<TrajectoryEntity> trajectories = study.getTrajectories();


            Map<String, Object> areasMap = new TreeMap<>();
            Map<String, Object> linksMap = new TreeMap<>();

            for (TrajectoryEntity trajectory : trajectories) {
                var trajectoryType = TrajectoryType.valueOf(trajectory.getType());

                switch (trajectoryType) {
                    case AREA -> buildAreasDataMap(study, trajectory, areasMap);
                    case LINK -> buildLinksDataMap(trajectory, linksMap);
                    case LOAD ->
                            log.warn("Load trajectory type is managed in AREA  trajectory: {}", trajectory.getFileName());
                    case THERMAL_CAPACITY, THERMAL_TECHNICAL_COMMON_PARAMETER, THERMAL_ECONOMIC_COST_PARAMETER, THERMAL_ECONOMIC_PARAMETER,
                         THERMAL_TECHNICAL_SPECIFIC_PARAMETER , THERMAL_TECHNICAL_MODULATION_PARAMETER->
                            log.warn("Thermal trajectories are managed in AREA  trajectory: {}", trajectory.getFileName());
                    case STS->
                            log.warn("STS trajectories are managed in AREA  trajectory: {}", trajectory.getFileName());
                    default -> throw TechnicalException.builder().message("Unhandled trajectory for generation: " + trajectoryType).build();
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
        } else {
            throw TechnicalException.builder().message("Study not found with ID: " + studyId).build();
        }

        return jsonForGenerator;
    }

    public Map<String, List<String>> getListArrowLoadFilesByAreaFromStudy(StudyEntity studyEntity) {
        Pattern pattern = Pattern.compile("_(.*?)[_\\.]");
        return studyEntity.getTrajectories().stream()
                .filter(this::isLoadTrajectoryWithEntities)
                .flatMap(trajectory -> processTrajectoryLoads(trajectory, studyEntity.getId(), pattern))
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                ));
    }

    private boolean isLoadTrajectoryWithEntities(TrajectoryEntity trajectory) {
        return "LOAD".equals(trajectory.getType())
                && trajectory.getLoadEntities() != null
                && !trajectory.getLoadEntities().isEmpty();
    }

    private Stream<Map.Entry<String, String>> processTrajectoryLoads(TrajectoryEntity trajectory, Integer studyId, Pattern pattern) {
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
            String outputFileName = generateAndSaveOutputFileName(loadEntity, trajectory);
            loadEntity.setOutPutFileName(outputFileName);
            loadRepository.save(loadEntity);
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
            return nasFileService.saveMatrixToNas(inputTxtFilePath, outputLoadDir);
        } catch (IOException e) {
            throw TechnicalException.builder().message(e.getMessage()).cause(e).build();
        }
    }

    private String extractAreaFromFileName(String fileName, Pattern pattern) {
        var matcher = pattern.matcher(fileName);
        return matcher.find() ? matcher.group(1).toUpperCase() : "OTHERS";
    }

    private boolean isLoadLinkedToStudy(LoadEntity loadEntity, int studyId) {
        return loadEntity.getTrajectoryEntities().stream()
                .flatMap(traj -> traj.getScenarioEntities().stream())
                .anyMatch(study -> study.getId().equals(studyId));
    }

    private void buildAreasDataMap(StudyEntity studyEntity, TrajectoryEntity trajectory, Map<String, Object> areasMap) {

        List<AreaDTO> areaDTOs = trajectory.getAreaConfigEntities().stream()
                .map(AreaMapper::toAreaDto)
                .toList();

        // Get LOAD files by area from all study trajectories
        Map<String, List<String>> listArrowLoadFilesByArea = getListArrowLoadFilesByAreaFromStudy(studyEntity);

        // Get thermal cluster generation DTOs for all trajectories in the study
        var areaClusterRefThermalClusterGenerationDtoMap = thermalPropertiesAssemblerService.assembleForTrajectories(studyEntity);

        var areaStsClusterGenerationDtoMap = stPropertiesAssemblerService.assembleStsProperties(studyEntity);

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
    }

    /**
     * This method should be enriched or simplified when we'll have
     * all configurations for area from input files
     */
    private static Map<String, Object> areasMapGenerator(AreaDTO areaDTO, List<String> arrowLoadFilesByArea, Map<String, ThermalClusterGenerationDto> clusterProps, Map<String, StsGenerationDTO> stsClusterProps) {
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
            return Collections.emptyMap();
        }

        Map<String, Object> clusterMap = new LinkedHashMap<>();

        clusterProps.forEach((clusterName, dto) -> {

            Map<String, Object> propertiesMap = PROPERTIES_MAPPER.convertValue(dto, new TypeReference<>() {});

            Map<String, Object> dataMap = DATA_MAPPER.convertValue(dto, new TypeReference<>() {});

            Map<String, Object> paramModulation = PARAM_MODULATION_MAPPER.convertValue(dto, new TypeReference<>() {});

            Map<String, Object> clusterData = new LinkedHashMap<>();
            clusterData.put(PROPERTIES, propertiesMap);
            clusterData.put("series", MATRIX_HASH);
            clusterData.put("fuel_cost", MATRIX_HASH);
            clusterData.put("co2_cost", MATRIX_HASH);
            clusterData.put(DATA, dataMap);
            clusterData.put("modulation", dto.getParamModulationTsList());
          //  clusterData.put(PARAM_MODULATION, paramModulation);

            clusterMap.put(clusterName, clusterData);
        });

        return clusterMap;
    }


    private static Map<String, Object> stsMapGenerator(String areaName, Map<String, StsGenerationDTO> stsClusterProps) {
        if (stsClusterProps == null || stsClusterProps.isEmpty()) {
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
                    clusterData.put("series", MATRIX_HASH);

                    stsClusterName.put(clusterName, clusterData);
                });

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
        String url = antaressDataManagerProperties.getGeneratorHostUrl() + "/generate_study/?study_id=" + studyId;

        try {
            webClient.post()
                    .uri(url)
                    .exchangeToMono(resp -> {
                        if (resp.statusCode().equals(HttpStatus.OK)) {
                            log.debug("Study {} has been successfully generated", studyId);
                            return resp.bodyToMono(String.class);
                        } else {
                            log.error("Error while generating study {{}}", studyId);
                            return resp.createException().flatMap(Mono::error);
                        }

                    })
                    .block();
        } catch (RuntimeException ex) {
            throw TechnicalException.builder()
                    .message("Error while call Generate study from generator " + studyId + ": " + ex.getMessage())
                    .cause(ex.getCause())
                    .build();
        }
    }
}

