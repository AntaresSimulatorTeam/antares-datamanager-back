package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.dto.TrajectoryDTO;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.scenario_builder.ScenarioBuilderFileProcessorService;
import com.rte_france.antares.datamanager_back.util.PathSecurityUtil;
import com.rte_france.antares.datamanager_back.validation.ValidTrajectoryName;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "ScenarioBuilder", description = "APIs for importing scenario builder trajectories")
public class ScenarioBuilderController {

    private final ScenarioBuilderFileProcessorService scenarioBuilderFileProcessorService;
    private final PathSecurityUtil pathSecurityUtil;

    @Operation(summary = "Import scenario builder trajectory from Excel file (scenario_builder_*.xlsx)")
    @PostMapping("/scenarioBuilder")
    public ResponseEntity<TrajectoryDTO> uploadScenarioBuilderTrajectory(
            @RequestParam("trajectoryToUse")
            @ValidTrajectoryName
            @Parameter(description = "Name of the trajectory file")
            String trajectoryToUse,

            @RequestParam("horizon")
            @Pattern(regexp = "^\\d{4}-\\d{4}$")
            @Parameter(description = "Horizon in format YYYY-YYYY (e.g., 2028-2029)")
            String horizon,

            @RequestParam(value = "studyId")
            @Parameter(description = "Study ID")
            Integer studyId)

            throws IOException {


        TrajectoryEntity trajectory = scenarioBuilderFileProcessorService.processScenarioBuilderFile(
                trajectoryToUse,
                horizon,
                studyId
        );
        return new ResponseEntity<>(
                toTrajectoryDTO(trajectory),
                HttpStatus.CREATED
        );
    }
}
