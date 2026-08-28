package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.dto.TrajectoryDTO;
import com.rte_france.antares.datamanager_back.service.p2g.P2gFileProcessorService;
import com.rte_france.antares.datamanager_back.validation.ValidTrajectoryName;
import io.swagger.v3.oas.annotations.Operation;
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
public class P2GController {
    private final P2gFileProcessorService p2gFileProcessorService;

    @Operation(summary = "import capacity cost trajectory to database ")
    @PostMapping("/capacity-cost-p2g")
    public ResponseEntity<TrajectoryDTO> uploadCapacityCostP2GTrajectory(
            @RequestParam("trajectoryToUse") @ValidTrajectoryName String trajectoryToUse,
            @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$") String horizon,
            @RequestParam("studyId") Integer studyId,
            @RequestParam("isCivilYear") boolean isCivilYear) throws IOException {

        return new ResponseEntity<>(toTrajectoryDTO(
                p2gFileProcessorService.processCapacityP2gFile(trajectoryToUse, horizon, studyId, isCivilYear)
        ), HttpStatus.CREATED);
    }

    @Operation(summary = "import modulation P2G trajectory to database ")
    @PostMapping("/modulation-p2g")
    public ResponseEntity<TrajectoryDTO> uploadModulationP2GTrajectory(
            @RequestParam("trajectoryToUse") @ValidTrajectoryName String trajectoryToUse,
            @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$") String horizon,
            @RequestParam("studyId") Integer studyId,
            @RequestParam("isCivilYear") boolean isCivilYear) throws Exception {

        return new ResponseEntity<>(toTrajectoryDTO(
                p2gFileProcessorService.processModulationP2gFile(trajectoryToUse, horizon, studyId, isCivilYear)
        ), HttpStatus.CREATED);
    }
}
