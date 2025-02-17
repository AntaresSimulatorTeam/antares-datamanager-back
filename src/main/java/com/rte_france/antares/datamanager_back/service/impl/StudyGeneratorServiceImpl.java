package com.rte_france.antares.datamanager_back.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rte_france.antares.datamanager_back.dto.AreaDTO;
import com.rte_france.antares.datamanager_back.mapper.AreaMapper;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.model.AreaConfigEntity;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.StudyGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class StudyGeneratorServiceImpl implements StudyGeneratorService {

    private final StudyRepository studyRepository;

    @Override
    public void studyTobeGenerated(Integer study_id) throws JsonProcessingException {
        Optional<StudyEntity> studyEntity = studyRepository.findById(study_id);
        if (studyEntity.isPresent()) {
            Set<TrajectoryEntity> trajectories = studyEntity.get().getTrajectories();
            for (TrajectoryEntity trajectory : trajectories) {
                var trajectoryType = trajectory.getType();

                switch (trajectoryType) {
                    case "AREA" -> buildAreasJson(trajectory);
                    case "LINK" -> buildLinkJson(trajectory);

                    default -> throw new IllegalArgumentException("Unexpected value: " + trajectoryType);
                }
            }
        }


    }


    private void buildAreasJson(TrajectoryEntity trajectory) throws JsonProcessingException {

        List<AreaConfigEntity> areaList = trajectory.getAreaConfigEntities();

        List<AreaEntity> areas = areaList.stream()
                .map(AreaConfigEntity::getArea)
                .toList();

        ObjectMapper objectMapper = new ObjectMapper();

        //TODO vargas check if only way to bypass Hybernate proxy issues
        List<AreaDTO> areaDTOs = AreaMapper.toAreaDTOs(areas);

        Map<String, Object> areaMap = areaObjectMapGeneratorJson();

        Map<String, Map<String, Object>> areasMap = areaDTOs.stream()
                .collect(Collectors.toMap(
                        AreaDTO::getName,
                        areaDTO -> areaMap
                ));

        Map<String, Object> finalMap = new HashMap<>();
        finalMap.put("areas", areasMap);

        String json= objectMapper.writeValueAsString(finalMap);


    }

    /**
     * This method should be enriched or simplified when we have
     * all configurations for area from input files
     */
    private static Map<String, Object> areaObjectMapGeneratorJson() {
        Map<String, Object> areaMap = new HashMap<>();
        areaMap.put("ui", "AreaUI class as JSON");
        areaMap.put("properties", "AreaProperties as JSON");

        Map<String, Object> hydroMap = new HashMap<>();
        hydroMap.put("properties", "HydroProperties as JSON");
        hydroMap.put("every matrices name inside HydroMatrixName enum", "matrix hash");

        areaMap.put("hydro", hydroMap);
        return areaMap;
    }

    private void buildLinkJson(TrajectoryEntity trajectory) throws JsonProcessingException {

    }


}

