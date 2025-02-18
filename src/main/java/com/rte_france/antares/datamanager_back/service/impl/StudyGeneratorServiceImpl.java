package com.rte_france.antares.datamanager_back.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rte_france.antares.datamanager_back.dto.AreaDTO;
import com.rte_france.antares.datamanager_back.mapper.AreaMapper;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.StudyGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class StudyGeneratorServiceImpl implements StudyGeneratorService {

    private final StudyRepository studyRepository;
    private final NasFileService nasFileService;

    @Override
    public void buildJsonForStudyGeneration(Integer study_id) throws JsonProcessingException {
        Map<String, Object> jsonForGenerator= jsonBuilder(study_id);
        ObjectMapper objectMapper = new ObjectMapper();
        String generatorJson = objectMapper.writeValueAsString(jsonForGenerator);
        try {
            nasFileService.saveFile(study_id + ".json",generatorJson.getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> jsonBuilder(Integer study_id) {
        Map<String, Object> jsonForGenerator = new TreeMap<>();
        String studyName;

        Optional<StudyEntity> studyEntity = studyRepository.findById(study_id);

        if (studyEntity.isPresent()) {
            Set<TrajectoryEntity> trajectories = studyEntity.get().getTrajectories();
            studyName = studyEntity.get().getName();

            Map<String, Object> areasMap = new TreeMap<>();
            Map<String, Object> linksMap = new TreeMap<>();

            for (TrajectoryEntity trajectory : trajectories) {
                var trajectoryType = trajectory.getType();

                switch (trajectoryType) {
                    case "AREA":
                        buildAreasDataMap(trajectory, areasMap);
                        break;
                    case "LINK":
                        buildLinksDataMap(trajectory, linksMap);
                        break;
                    default:
                        throw new IllegalArgumentException("Unexpected value: " + trajectoryType);
                }
            }

            Map<String, Object> innerGeneratorMap = new TreeMap<>();
            innerGeneratorMap.put("version", "880");
            innerGeneratorMap.put("settings", "will be refactored so we'll put nothing for the moment");
            innerGeneratorMap.put("areas", areasMap);
            innerGeneratorMap.put("links", linksMap);

            jsonForGenerator.put(studyName, innerGeneratorMap);
        } else {
            throw new IllegalArgumentException("Study not found with ID: " + study_id);
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

        Map<String, Object> linkMap = linksMapGenerator();

        Map<String, Map<String, Object>> linksDataMap =linkEntityList.stream()
                .collect(Collectors.toMap(
                        linkEntity -> linkEntity.getName().replace("-", "/"),
                        linkEntity -> linkMap,
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

}

