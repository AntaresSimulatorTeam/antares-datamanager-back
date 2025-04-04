package com.rte_france.antares.datamanager_back.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.AreaDTO;
import com.rte_france.antares.datamanager_back.exception.TechnicalAntaresDataMangerException;
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
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class StudyGeneratorServiceImpl implements StudyGeneratorService {

    private final StudyRepository studyRepository;

    private final NasFileService nasFileService;

    private final WebClient webClient;

    private final AntaressDataManagerProperties antaressDataManagerProperties;

    @ExecutionTime
    @Override
    public void buildJsonForStudyGeneration(Integer studyId) throws JsonProcessingException {
        Map<String, Object> jsonForGenerator = jsonBuilder(studyId);
        ObjectMapper objectMapper = new ObjectMapper();
        String generatorJson = objectMapper.writeValueAsString(jsonForGenerator);
        try {
            nasFileService.saveFile(studyId + ".json", generatorJson.getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> jsonBuilder(Integer studyId) {
        Map<String, Object> jsonForGenerator = new TreeMap<>();
        String studyName;

        Optional<StudyEntity> studyEntity = studyRepository.findById(studyId);

        if (studyEntity.isPresent()) {
            Set<TrajectoryEntity> trajectories = studyEntity.get().getTrajectories();
            studyName = studyEntity.get().getName();

            Map<String, Object> areasMap = new TreeMap<>();
            Map<String, Object> linksMap = new TreeMap<>();

            for (TrajectoryEntity trajectory : trajectories) {
                var trajectoryType = trajectory.getType();

                switch (trajectoryType) {
                    case "AREA" -> buildAreasDataMap(trajectory, areasMap);
                    case "LINK" -> buildLinksDataMap(trajectory, linksMap);
                    default -> throw new IllegalArgumentException("Unexpected value: " + trajectoryType);
                }
            }

            Map<String, Object> innerGeneratorMap = new TreeMap<>();
            innerGeneratorMap.put("version", "880");
            innerGeneratorMap.put("settings", "will be refactored so we'll put nothing for the moment");
            innerGeneratorMap.put("areas", areasMap);
            innerGeneratorMap.put("links", linksMap);

            jsonForGenerator.put(studyName, innerGeneratorMap);
        } else {
            throw new IllegalArgumentException("Study not found with ID: " + studyId);
        }

        return jsonForGenerator;
    }


    private void buildAreasDataMap(TrajectoryEntity trajectory, Map<String, Object> areasMap) {

        List<AreaConfigEntity> areaList = trajectory.getAreaConfigEntities();

        List<AreaEntity> areasEntities = areaList.stream()
                .map(AreaConfigEntity::getArea)
                .toList();


        List<AreaDTO> areaDTOs = AreaMapper.toAreaDTOs(areasEntities);

        Map<String, Object> generatedAreas = areasMapGenerator();

        Map<String, Map<String, Object>> areasDataMap = areaDTOs.stream()
                .collect(Collectors.toMap(
                        AreaDTO::getName,
                        areaDTO -> generatedAreas
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
    private static Map<String, Object> areasMapGenerator() {
        Map<String, Object> areaMap = new HashMap<>();
        areaMap.put("ui", "AreaUI class as JSON");
        areaMap.put("properties", "AreaProperties as JSON");

        Map<String, Object> hydroMap = new HashMap<>();
        hydroMap.put("properties", "HydroProperties as JSON");
        hydroMap.put("every matrices name inside HydroMatrixName enum", "matrix hash");

        areaMap.put("hydro", hydroMap);
        return areaMap;
    }

    private static Map<String, Object> linksMapGenerator() {
        Map<String, Object> linkMap = new HashMap<>();
        linkMap.put("properties", "LinkProperties as JSON");
        linkMap.put("ui", "LinkUi class as JSON");
        linkMap.put("parameters", "matrix hash");
        linkMap.put("capacity_direct", "matrix hash");
        linkMap.put("capacity_indirect", "matrix hash");

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
                            log.debug(String.format("Study {%s} has been successfully generated", studyId));
                            return resp.bodyToMono(String.class);
                        } else {
                            log.error(String.format("Error while generating study {%s}", studyId));
                            return resp.createException().flatMap(Mono::error);
                        }

                    })
                    .block();
        } catch (RuntimeException ex) {
            throw new TechnicalAntaresDataMangerException("Error while generating study: " + ex.getMessage());
        }
    }
}

