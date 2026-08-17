package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.dto.TrajectoryDTO;
import com.rte_france.antares.datamanager_back.service.dsr.DsrCapacityModulationFileProcessorService;
import com.rte_france.antares.datamanager_back.service.dsr.DsrFileProcessorService;
import com.rte_france.antares.datamanager_back.validation.ValidTrajectoryName;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
public class DsrController {
    private final DsrFileProcessorService dsrFileProcessorService;
    private final DsrCapacityModulationFileProcessorService dsrCapacityModulationFileProcessorService;

    @Operation(summary = "import DSR cluster trajectory to database ")
    @PostMapping("/dsr-cluster")
    public ResponseEntity<TrajectoryDTO> uploadDsrClusterTrajectory(@RequestParam("area") String area,
                                                                    @RequestParam("trajectoryToUse") @ValidTrajectoryName String trajectoryToUse,
                                                                    @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$")
                                                                    @Parameter(description = "example of horizon : 2020-2021") String horizon,
                                                                    @RequestParam("studyId") Integer studyId,
                                                                    @RequestParam("isCivilYear") boolean isCivilYear) throws IOException {

        return new ResponseEntity<>(toTrajectoryDTO(
                dsrFileProcessorService.processDsrClusterFile(trajectoryToUse, horizon, studyId, isCivilYear, area)
        ), HttpStatus.CREATED);
    }

    @Operation(summary = "import DSR capacity modulation trajectory to database ")
    @PostMapping("/dsr-capacity-modulation")
    public ResponseEntity<TrajectoryDTO> uploadDsrCapacityModulationTrajectory(
            @RequestParam("trajectoryToUse") @ValidTrajectoryName String trajectoryToUse,
            @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$")
            @Parameter(description = "example of horizon : 2020-2021") String horizon,
            @RequestParam("studyId") Integer studyId) throws IOException {
        return new ResponseEntity<>(toTrajectoryDTO(
                dsrCapacityModulationFileProcessorService.processDsrCapacityModulationFile(trajectoryToUse, horizon, studyId)
        ), HttpStatus.CREATED);
    }
}
