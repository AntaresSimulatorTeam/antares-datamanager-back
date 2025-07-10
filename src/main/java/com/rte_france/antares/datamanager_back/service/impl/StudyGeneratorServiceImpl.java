package com.rte_france.antares.datamanager_back.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.AreaDTO;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.mapper.AreaMapper;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.StudyGeneratorService;
import com.rte_france.antares.datamanager_back.util.ExecutionTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class StudyGeneratorServiceImpl implements StudyGeneratorService {

    private final StudyRepository studyRepository;

    private final NasFileService nasFileService;

    private final WebClient webClient;

    private final AntaressDataManagerProperties antaressDataManagerProperties;

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
                var trajectoryType = trajectory.getType();

                switch (trajectoryType) {
                    case "AREA" -> buildAreasDataMap(study, trajectory, areasMap);
                    case "LINK" -> buildLinksDataMap(trajectory, linksMap);
                    case "LOAD" ->
                            log.warn("Load trajectory type is managed in AREA  trajectory: {}", trajectory.getFileName());

                    default ->
                            throw TechnicalException.builder().message("Unexpected value: " + trajectoryType).build();
                }
            }

            Map<String, Object> innerGeneratorMap = new TreeMap<>();
            innerGeneratorMap.put("version", "880");
            innerGeneratorMap.put("settings", "will be refactored so we'll put nothing for the moment");
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
                .filter(trajectory -> "LOAD".equals(trajectory.getType()) && trajectory.getLoadEntities() != null && !trajectory.getLoadEntities().isEmpty())
                .flatMap(trajectory -> {
                    if ("OTHERS".equals(trajectory.getLoadArea())) {
                        // Pour chaque outputFileName, extraire le loadArea du nom de fichier
                        return trajectory.getLoadEntities().stream()
                                .filter(loadEntity -> {
                                  log.warn("load {}", loadEntity);
                                  return isLoadLinkedToStudy(loadEntity, studyEntity.getId());
                                })
                                .map(loadEntity -> {
                                    log.warn(String.valueOf(loadEntity));
                                    String fileName = loadEntity.getOutPutFileName();
                                    Matcher matcher = pattern.matcher(fileName);
                                    String area = matcher.find() ? matcher.group(1) : "OTHERS";
                                    return Map.entry(area.toUpperCase(), fileName);
                                });
                    } else {
                        // Cas classique
                        return trajectory.getLoadEntities().stream()
                                .map(loadEntity -> Map.entry(trajectory.getLoadArea().toUpperCase(), loadEntity.getOutPutFileName()));
                    }
                })
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                ));
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
        Map<String, Map<String, Object>> areasDataMap = areaDTOs.stream()
                .collect(Collectors.toMap(
                        AreaDTO::getName,
                        areaDTO -> areasMapGenerator(listArrowLoadFilesByArea.get(areaDTO.getName()))
                ));

        areasMap.putAll(areasDataMap);

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
    private static Map<String, Object> areasMapGenerator(List<String> arrowLoadFilesByArea) {
        // This is a placeholder for the actual AreaUI and AreaProperties classes
        // Replace with actual implementations or JSON representations
        Map<String, Object> areaMap = new HashMap<>();
        areaMap.put("ui", "AreaUI class as JSON");
        areaMap.put(PROPERTIES, "AreaProperties as JSON");

        Map<String, Object> hydroMap = new HashMap<>();
        hydroMap.put(PROPERTIES, "HydroProperties as JSON");
        hydroMap.put("every matrices name inside HydroMatrixName enum", MATRIX_HASH);

        areaMap.put("hydro", hydroMap);
        areaMap.put("loads", arrowLoadFilesByArea != null && !arrowLoadFilesByArea.isEmpty() ? arrowLoadFilesByArea : "No LOAD files for this area");
        return areaMap;
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

