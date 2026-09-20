package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.dto.TrajectoryDTO;
import com.rte_france.antares.datamanager_back.service.hydro.HydroMeFileProcessorService;
import com.rte_france.antares.datamanager_back.validation.ValidTrajectoryName;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Pattern;
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
public class HydroMeController {

    private final HydroMeFileProcessorService hydroMeFileProcessorService;

    @Operation(summary = "import HYDRO CAPACITY ME trajectory to database")
    @PostMapping("/hydro-capacity-me")
    public ResponseEntity<TrajectoryDTO> uploadHydroCapacityMeTrajectory(
            @RequestParam("trajectoryToUse") @ValidTrajectoryName String trajectoryToUse,
            @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$")
            @Parameter(description = "example of horizon : 2020-2021") String horizon,
            @RequestParam("studyId") Integer studyId) throws IOException {

        return new ResponseEntity<>(toTrajectoryDTO(hydroMeFileProcessorService.processHydroCapacityMeFile(trajectoryToUse, horizon, studyId)), HttpStatus.CREATED);
    }
}
