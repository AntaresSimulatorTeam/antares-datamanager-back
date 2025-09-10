package com.rte_france.antares.datamanager_back.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.AreaDTO;
import com.rte_france.antares.datamanager_back.dto.ThermalClusterPropertiesDto;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.mapper.AreaMapper;
import com.rte_france.antares.datamanager_back.repository.LoadRepository;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.LoadFileProcessorService;
import com.rte_france.antares.datamanager_back.service.StudyGeneratorService;
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

    private final StudyRepository studyRepository;

    private final LoadRepository loadRepository;

    private final NasFileService nasFileService;

    private final WebClient webClient;

    private final AntaressDataManagerProperties antaressDataManagerProperties;

    private final LoadFileProcessorService loadFileProcessorService;

    private final ThermalPropertiesAssemblerService thermalPropertiesAssemblerService;

    private static final String PROPERTIES = "properties";

    private static final String MATRIX_HASH = "matrix hash";


    @ExecutionTime
    @Override
    public void buildJsonForStudyGeneration(Integer studyId) throws TechnicalException {
        Map<String, Object> jsonStudyDataForGeneration = buildJsonStudyDataForGeneration(studyId);
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String generatorJson = objectMapper.writeValueAsString(jsonStudyDataForGeneration);
            nasFileService.saveFile(studyId + ".json", generatorJson.getBytes());
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
                var trajectoryType =  TrajectoryType.valueOf(trajectory.getType());

                switch (trajectoryType) {
                    case AREA -> buildAreasDataMap(study, trajectory, areasMap);
                    case LINK -> buildLinksDataMap(trajectory, linksMap);
                    case LOAD ->
                            log.warn("Load trajectory type is managed in AREA  trajectory: {}", trajectory.getFileName());
                    case THERMAL_CAPACITY, THERMAL_PARAMETER, THERMAL_COST ->
                            log.warn("Thermal trajectories are managed in AREA  trajectory: {}", trajectory.getFileName());
                    default ->
                            throw TechnicalException.builder().message("Unhandled type: " + trajectoryType).build();
                }
            }

            Map<String, Object> innerGeneratorMap = new TreeMap<>();
            innerGeneratorMap.put("version", "880");
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
    var inputTxtFilePath = Paths.get(
            antaressDataManagerProperties.getNasDirectory(),
            antaressDataManagerProperties.getTrajectoryFilePath(),
            antaressDataManagerProperties.getLoadDirectory(),
            trajectory.getFileName(),
            loadEntity.getFileName()
    ).normalize();

    try {
      return loadFileProcessorService.saveMatrixToNas(inputTxtFilePath);
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

        List<AreaConfigEntity> areaList = trajectory.getAreaConfigEntities();

        List<AreaEntity> areasEntities = areaList.stream()
                .map(AreaConfigEntity::getArea)
                .toList();


        List<AreaDTO> areaDTOs = AreaMapper.toAreaDTOs(areasEntities);


        Map<String, List<String>> listArrowLoadFilesByArea = getListArrowLoadFilesByAreaFromStudy(studyEntity);

//        var areaRefProps = thermalPropertiesAssemblerService.assembleForTrajectories(studyEntity.getTrajectories()); // TODO: uncomment this and remove next line for thermal gen
        var areaRefProps = new HashMap<ThermalPropertiesAssemblerService.AreaRefKey, ThermalClusterPropertiesDto>(); // TODO: remove and uncomment line before for thermal gen

        Map<String, Map<String, Object>> areasDataMap = areaDTOs.stream()
                .collect(Collectors.toMap(
                        AreaDTO::getName,
                        areaDTO -> areasMapGenerator(
                                listArrowLoadFilesByArea.get(areaDTO.getName()),
                                getClusterPropsForArea(areaRefProps, areaDTO.getName())
                        )
                ));

        areasMap.putAll(areasDataMap);

    }

    private static Map<String, ThermalClusterPropertiesDto> getClusterPropsForArea(Map<ThermalPropertiesAssemblerService.AreaRefKey, ThermalClusterPropertiesDto> areaRefProps, String areaName) {
        return areaRefProps.entrySet().stream()
                .filter(e -> e.getKey().area().equalsIgnoreCase(areaName))
                .collect(Collectors.toMap(
                        e -> e.getKey().area().toUpperCase(Locale.ROOT) + "_" + e.getKey().ref().getName(),
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
    private static Map<String, Object> areasMapGenerator(List<String> arrowLoadFilesByArea, Map<String, ThermalClusterPropertiesDto> clusterProps) {
        // This is a placeholder for the actual AreaUI and AreaProperties classes
        // Replace with actual implementations or JSON representations
        Map<String, Object> areaMap = new HashMap<>();
        areaMap.put("ui", "AreaUI class as JSON");
        areaMap.put(PROPERTIES, "AreaProperties as JSON");

        Map<String, Object> hydroMap = new HashMap<>();
        hydroMap.put(PROPERTIES, "HydroProperties as JSON");
        hydroMap.put("every matrices name inside HydroMatrixName enum", MATRIX_HASH);

        Map<String, Object> thermalsMap = thermalsMapGenerator(clusterProps);

        areaMap.put("hydro", hydroMap);
        areaMap.put("loads", arrowLoadFilesByArea != null && !arrowLoadFilesByArea.isEmpty() ? arrowLoadFilesByArea : "No LOAD files for this area");
        areaMap.put("thermals", thermalsMap);

        return areaMap;
    }

    private static Map<String, Object> thermalsMapGenerator(
            Map<String, ThermalClusterPropertiesDto> clusterProps
    ) {
        if (clusterProps == null || clusterProps.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> clusterMap = new LinkedHashMap<>();

        clusterProps.forEach((clusterName, dto) -> {
            Map<String, Object> clusterData = new HashMap<>();
            clusterData.put(PROPERTIES, dto);
            clusterData.put("series", MATRIX_HASH);
            clusterData.put("fuel_cost", MATRIX_HASH);
            clusterData.put("co2_cost", MATRIX_HASH);
            clusterData.put("data", MATRIX_HASH);
            clusterData.put("modulation", MATRIX_HASH);

            clusterMap.put(clusterName, clusterData);
        });

        return clusterMap;
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

