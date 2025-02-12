package com.rte_france.antares.datamanager_back.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rte_france.antares.datamanager_back.dto.StudyDTO;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.StudyGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.rte_france.antares.datamanager_back.dto.TrajectoryType.AREA;
import static com.rte_france.antares.datamanager_back.dto.TrajectoryType.LINK;


@Slf4j
@Service
@RequiredArgsConstructor
public class StudyGeneratorServiceImpl implements StudyGeneratorService {

    private final StudyRepository studyRepository;

    @Override
    public void studyTobeGenerated(StudyDTO studyDTO) throws JsonProcessingException {
        Optional<StudyEntity> studyEntity = studyRepository.findById(studyDTO.getId());
        if (studyEntity.isPresent()) {
            Set<TrajectoryEntity> trajectories = studyEntity.get().getTrajectories();
            for (TrajectoryEntity trajectory : trajectories) {
                var trajectoryType = trajectory.getType();

                switch (trajectoryType) {
                    case "AREA" -> handleAreaTrajectory(trajectory);
                    case "LINK" -> handleLinkTrajectory(trajectory);

                    default -> throw new IllegalArgumentException("Unexpected value: " + trajectoryType);
                }
            }
        }


    }


    private void handleAreaTrajectory(TrajectoryEntity trajectory) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> areas = new HashMap<>();
        //areas.put(areaEntity.getName(), areaJson);

//        Path filePath =
//        Files.createDirectories(filePath.getParent());
//
//        // Write the JSON to the file
//        Files.write(filePath, objectMapper.writeValueAsBytes(areas));

    }

    private void handleLinkTrajectory(TrajectoryEntity trajectory) throws JsonProcessingException {

    }


}

