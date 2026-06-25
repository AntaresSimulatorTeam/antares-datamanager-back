package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.dto.TrajectoryDTO;
import com.rte_france.antares.datamanager_back.service.nuclear.NuclearFileProcessorService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

import static com.rte_france.antares.datamanager_back.mapper.TrajectoryMapper.toTrajectoryDTO;

@Slf4j
@RestController
@Validated
@RequestMapping("/v1/trajectory")
@RequiredArgsConstructor
public class NuclearController {

    private final NuclearFileProcessorService nuclearFileProcessorService;

    @Operation(summary = "Import nuclear modulation trajectory to database")
    @PostMapping("/nuclear-modulation")
    public ResponseEntity<TrajectoryDTO> uploadNuclearModulationTrajectory(
            @RequestParam("trajectoryToUse") @Size(max = 40, message = "Trajectory name cannot exceed 40 characters") @Pattern(regexp = "^[a-zA-Z0-9_-]+$") String trajectoryToUse,
            @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$") String horizon,
            @RequestParam("studyId") Integer studyId,
            @RequestParam("area") @Size(max = 40, message = "Area name cannot exceed 40 characters") @Pattern(regexp = "^[a-zA-Z0-9_-]+$") String area) throws IOException {

        return new ResponseEntity<>(toTrajectoryDTO(nuclearFileProcessorService.processNuclearModulationFile(trajectoryToUse, horizon, studyId, area)), HttpStatus.CREATED);
    }

    @Operation(summary = "Import nuclear long-term trajectory to database")
    @PostMapping("/nuclear-lt")
    public ResponseEntity<TrajectoryDTO> uploadNuclearLongTermTrajectory(
            @RequestParam("trajectoryToUse") @Size(max = 40, message = "Trajectory name cannot exceed 40 characters") @Pattern(regexp = "^[a-zA-Z0-9_-]+$") String trajectoryToUse,
            @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$") String horizon,
            @RequestParam("studyId") Integer studyId,
            @RequestParam("area") @Size(max = 40, message = "Area name cannot exceed 40 characters") @Pattern(regexp = "^[a-zA-Z0-9_-]+$") String area) throws IOException {

        return new ResponseEntity<>(toTrajectoryDTO(nuclearFileProcessorService.processNuclearLongTermFile(trajectoryToUse, horizon, studyId, area)), HttpStatus.CREATED);
    }

    @Operation(summary = "Import nuclear EPR time series trajectory to database")
    @PostMapping("/nuclear-ts-erp")
    public ResponseEntity<TrajectoryDTO> uploadNuclearTsErpTrajectory(
            @RequestParam("trajectoryToUse") @Size(max = 40, message = "Trajectory name cannot exceed 40 characters") @Pattern(regexp = "^[a-zA-Z0-9_.-]+$") String trajectoryToUse,
            @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$") String horizon,
            @RequestParam("studyId") Integer studyId,
            @RequestParam(value = "area", required = false) @Size(max = 40, message = "Area name cannot exceed 40 characters") @Pattern(regexp = "^[a-zA-Z0-9_-]*$") String area) throws IOException {

        return new ResponseEntity<>(toTrajectoryDTO(nuclearFileProcessorService.processNuclearTsErpFile(trajectoryToUse, horizon, studyId, area)), HttpStatus.CREATED);
    }

    @Operation(summary = "Import nuclear SMR time series trajectory to database")
    @PostMapping("/nuclear-ts-smr")
    public ResponseEntity<TrajectoryDTO> uploadNuclearTsSmrTrajectory(
            @RequestParam("trajectoryToUse") @Size(max = 40, message = "Trajectory name cannot exceed 40 characters") @Pattern(regexp = "^[a-zA-Z0-9_.-]+$") String trajectoryToUse,
            @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$") String horizon,
            @RequestParam("studyId") Integer studyId,
            @RequestParam(value = "area", required = false) @Size(max = 40, message = "Area name cannot exceed 40 characters") @Pattern(regexp = "^[a-zA-Z0-9_-]*$") String area) throws IOException {

        return new ResponseEntity<>(toTrajectoryDTO(nuclearFileProcessorService.processNuclearTsSmrFile(trajectoryToUse, horizon, studyId, area)), HttpStatus.CREATED);
    }

    @Operation(summary = "Import nuclear Talon trajectory to database")
    @PostMapping("/nuclear-talon")
    public ResponseEntity<TrajectoryDTO> uploadNuclearTalonTrajectory(
            @RequestParam("trajectoryToUse") @Size(max = 40, message = "Trajectory name cannot exceed 40 characters") @Pattern(regexp = "^[a-zA-Z0-9_.-]+$") String trajectoryToUse,
            @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$") String horizon,
            @RequestParam("studyId") Integer studyId,
            @RequestParam(value = "area", required = false) @Size(max = 40, message = "Area name cannot exceed 40 characters") @Pattern(regexp = "^[a-zA-Z0-9_-]*$") String area) throws IOException {

        return new ResponseEntity<>(toTrajectoryDTO(nuclearFileProcessorService.processNuclearTalonFile(trajectoryToUse, horizon, studyId, area)), HttpStatus.CREATED);
    }
}
