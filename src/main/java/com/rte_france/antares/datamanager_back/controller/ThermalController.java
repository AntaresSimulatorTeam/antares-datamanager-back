package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.dto.TrajectoryDTO;
import com.rte_france.antares.datamanager_back.service.ThermalFileProcessorService;
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


    @Operation(summary = "import Trajectory load to database ")
    @PostMapping("/thermal-capacity")
    public ResponseEntity<TrajectoryDTO> uploadTrajectory(@RequestParam("area") String area,
                                                          @RequestParam("trajectoryToUse") String trajectoryToUse,
                                                          @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$")
                                                          @Parameter(description = "example of horizon : 2020-2021") String horizon,
                                                          @RequestParam("studyId") Integer studyId,
                                                          @RequestParam("isCivilYear") boolean isCivilYear) throws IOException {

        return new ResponseEntity<>(toTrajectoryDTO(trajectoryService.processThermalCapacityTrajectory(trajectoryToUse, horizon, studyId, isCivilYear,area)), HttpStatus.CREATED);
    }
}
