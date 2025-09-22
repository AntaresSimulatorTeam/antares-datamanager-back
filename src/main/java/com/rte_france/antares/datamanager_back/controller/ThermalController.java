package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.dto.TrajectoryDTO;
import com.rte_france.antares.datamanager_back.service.TrajectoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

import static com.rte_france.antares.datamanager_back.mapper.TrajectoryMapper.toTrajectoryDTO;

@Slf4j
@RestController
@RequestMapping("/v1/trajectory")
@RequiredArgsConstructor
public class ThermalController {
    private final TrajectoryService trajectoryService;


    @Operation(summary = "import thermal capacity trajectory to database ")
    @PostMapping("/thermal-capacity")
    public ResponseEntity<TrajectoryDTO> uploadThermalCapacityTrajectory(@RequestParam("area") String area, // FR, // GB, DE, IT, ES, PT, BE, NL, LU, CH //OTHER
                                                         @RequestParam(value = "technology", required = false) String technology,
                                                         @RequestParam("trajectoryToUse") String trajectoryToUse,
                                                         @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$")
                                                         @Parameter(description = "example of horizon : 2020-2021") String horizon,
                                                         @RequestParam("studyId") Integer studyId,
                                                         @RequestParam("isCivilYear") boolean isCivilYear) throws IOException {

        return new ResponseEntity<>(toTrajectoryDTO(
                trajectoryService.processThermalCapacityTrajectory(trajectoryToUse, horizon, studyId, isCivilYear, area, technology)
        ), HttpStatus.CREATED);
    }

    @Operation(summary = "import thermal common parameters trajectory to database ")
    @PostMapping("/thermal-common-parameter")
    public ResponseEntity<TrajectoryDTO> uploadThermalParameterTrajectory(
            @RequestParam("trajectoryToUse") String trajectoryToUse,
            @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$")
            @Parameter(description = "example of horizon : 2020-2021") String horizon,
            @RequestParam("studyId") Integer studyId) throws IOException {
        return new ResponseEntity<>(toTrajectoryDTO(
                trajectoryService.processThermalCommonParameterTrajectory(trajectoryToUse, horizon, studyId)
        ), HttpStatus.CREATED);
    }


    @Operation(summary = "import thermal specific parameters trajectory to database ")
    @PostMapping("/thermal-specific-parameter")
    public ResponseEntity<TrajectoryDTO> uploadThermalSpecificParameterTrajectory(
            @RequestParam("area") String area,
            @RequestParam("trajectoryToUse") String trajectoryToUse,
            @RequestParam("horizon") String horizon,
            @RequestParam("studyId") Integer studyId) throws IOException {
        return new ResponseEntity<>(toTrajectoryDTO(
                trajectoryService.processThermalSpecificParameterTrajectory(trajectoryToUse, horizon, area, studyId)
        ), HttpStatus.CREATED);
    }
}
