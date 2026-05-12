package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.dto.TrajectoryDTO;
import com.rte_france.antares.datamanager_back.service.hydro.HydroFileProcessorService;
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

import java.io.IOException;

import static com.rte_france.antares.datamanager_back.mapper.TrajectoryMapper.toTrajectoryDTO;

@Slf4j
@RestController
@Validated
@RequestMapping("/v1/trajectory")
@RequiredArgsConstructor
public class HydroController {

    private final HydroFileProcessorService hydroFileProcessorService;

    @Operation(summary = "import HYDRO series trajectory to database ")
    @PostMapping("/hydro-series")
    public ResponseEntity<TrajectoryDTO> uploadSeriesHydroTrajectory(
            @RequestParam("trajectoryToUse") @Size(max = 40, message = "Trajectory name cannot exceed 40 characters") @Pattern(regexp = "^[a-zA-Z0-9_-]+$") String trajectoryToUse,
            @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$") String horizon,
            @RequestParam("studyId") Integer studyId,
            @RequestParam("area") String area,
            @RequestParam("isCivilYear") boolean isCivilYear) throws IOException {

        return new ResponseEntity<>(toTrajectoryDTO(hydroFileProcessorService.processHydroSeriesFile(trajectoryToUse, horizon, studyId, area, isCivilYear)), HttpStatus.CREATED);
    }
}