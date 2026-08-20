package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryDTO;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.service.flowbased.FlowbasedFileProcessorService;
import com.rte_france.antares.datamanager_back.validation.ValidTrajectoryName;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.nio.file.Paths;

import static com.rte_france.antares.datamanager_back.mapper.TrajectoryMapper.toTrajectoryDTO;

@Slf4j
@RestController
@Validated
@RequestMapping("/v1/trajectory")
@RequiredArgsConstructor
public class FlowbasedController {
    private final FlowbasedFileProcessorService flowbasedFileProcessorService;
    private final AntaresDataManagerProperties antaresDataManagerProperties;

    @Operation(summary = "import flowbased trajectory to database")
    @PostMapping("/flowbased")
    public ResponseEntity<TrajectoryDTO> uploadFlowbasedTrajectory(
            @RequestParam("trajectoryToUse") @ValidTrajectoryName  String trajectoryToUse,
            @RequestParam(value = "studyId", required = false) Integer studyId,
            @RequestParam("horizon") String horizon) {
        
        String[] parts = trajectoryToUse.split("###");
        if (parts.length != 2) {
            throw BusinessException.builder()
                    .message("Trajectory name must be in format: repertoire1###repertoire2")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        
        String repertoire1 = parts[0];
        String repertoire2 = parts[1];
        
        Path trajectoryFilePath = Paths.get(
                antaresDataManagerProperties.getNasDirectory(),
                antaresDataManagerProperties.getTrajectoryFilePath(),
                antaresDataManagerProperties.getFlowbasedDirectory(),
                repertoire1,
                repertoire2
        );
        
        return new ResponseEntity<>(toTrajectoryDTO(
                flowbasedFileProcessorService.processFlowbasedFiles(trajectoryFilePath, trajectoryToUse, studyId, horizon)
        ), HttpStatus.CREATED);
    }
}
