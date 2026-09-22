package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.dto.TrajectoryDTO;
import com.rte_france.antares.datamanager_back.service.common.TrajectoryService;
import com.rte_france.antares.datamanager_back.service.hydro.HydroMeFileProcessorService;
import com.rte_france.antares.datamanager_back.service.hydro.HydroParametersMeFileProcessorService;
import com.rte_france.antares.datamanager_back.util.PathSecurityUtil;
import com.rte_france.antares.datamanager_back.validation.ValidTrajectoryName;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Path;

import static com.rte_france.antares.datamanager_back.mapper.TrajectoryMapper.toTrajectoryDTO;


@Slf4j
@RestController
@Validated
@RequestMapping("/v1/trajectory")
@RequiredArgsConstructor
public class MultiEnergyController {

    private final TrajectoryService trajectoryService;
    private final PathSecurityUtil pathSecurityUtil;
    private final HydroMeFileProcessorService hydroMeFileProcessorService;
    private final HydroParametersMeFileProcessorService hydroParametersMeFileProcessorService;

    @Operation(summary = "import Trajectory load ME to database ")
    @PostMapping("/load-me")
    public ResponseEntity<TrajectoryDTO> uploadLoadMeTrajectory(@RequestParam("trajectoryToUse") @ValidTrajectoryName String trajectoryToUse,
                                                               @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$")
                                                               @Parameter(description = "example of horizon : 2020-2021") String horizon,
                                                               @RequestParam("studyId") Integer studyId) throws IOException {
        pathSecurityUtil.resolveSafePath(
                properties -> Path.of(properties.getNasDirectory(), properties.getTrajectoryFilePath()),
                trajectoryToUse
        );
        return new ResponseEntity<>(toTrajectoryDTO(trajectoryService.processLoadMeTrajectory(trajectoryToUse, horizon, studyId)), HttpStatus.CREATED);
    }

    @Operation(summary = "import CONSTRAINT_ME trajectory file to database")
    @PostMapping("/constraint-me")
    public ResponseEntity<TrajectoryDTO> uploadConstraintMeTrajectory(@RequestParam("trajectoryToUse") @ValidTrajectoryName String trajectoryToUse,
                                                                     @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$")
                                                                     @Parameter(description = "example of horizon : 2020-2021") String horizon,
                                                                     @RequestParam("studyId") Integer studyId) throws IOException {
        pathSecurityUtil.resolveSafePath(
                properties -> Path.of(properties.getNasDirectory(), properties.getTrajectoryFilePath()),
                trajectoryToUse
        );
        return new ResponseEntity<>(toTrajectoryDTO(trajectoryService.processConstraintMeTrajectory(trajectoryToUse, horizon, studyId)), HttpStatus.CREATED);
    }

    @Operation(summary = "import EFFICIENCY_ME trajectory file to database")
    @PostMapping("/efficiency-me")
    public ResponseEntity<TrajectoryDTO> uploadEfficiencyMeTrajectory(@RequestParam("trajectoryToUse") @ValidTrajectoryName String trajectoryToUse,
                                                                     @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$")
                                                                     @Parameter(description = "example of horizon : 2020-2021") String horizon,
                                                                     @RequestParam("studyId") Integer studyId) throws IOException {
        pathSecurityUtil.resolveSafePath(
                properties -> Path.of(properties.getNasDirectory(), properties.getTrajectoryFilePath()),
                trajectoryToUse
        );
        return new ResponseEntity<>(toTrajectoryDTO(trajectoryService.processEfficiencyMeTrajectory(trajectoryToUse, horizon, studyId)), HttpStatus.CREATED);
    }

    @Operation(summary = "import HYDRO CAPACITY ME trajectory to database")
    @PostMapping("/hydro-capacity-me")
    public ResponseEntity<TrajectoryDTO> uploadHydroCapacityMeTrajectory(
            @RequestParam("trajectoryToUse") @ValidTrajectoryName String trajectoryToUse,
            @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$")
            @Parameter(description = "example of horizon : 2020-2021") String horizon,
            @RequestParam("studyId") Integer studyId) throws IOException {

        return new ResponseEntity<>(toTrajectoryDTO(hydroMeFileProcessorService.processHydroCapacityMeFile(trajectoryToUse, horizon, studyId)), HttpStatus.CREATED);
    }

    @Operation(summary = "import HYDRO PARAMETERS ME trajectory to database")
    @PostMapping("/hydro-parameters-me")
    public ResponseEntity<TrajectoryDTO> uploadHydroParametersMeTrajectory(
            @RequestParam("trajectoryToUse") @ValidTrajectoryName String trajectoryToUse,
            @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$")
            @Parameter(description = "example of horizon : 2020-2021") String horizon,
            @RequestParam("studyId") Integer studyId) throws IOException {
        pathSecurityUtil.resolveSafePath(
                properties -> Path.of(properties.getNasDirectory(), properties.getTrajectoryFilePath()),
                trajectoryToUse
        );
        return new ResponseEntity<>(toTrajectoryDTO(hydroParametersMeFileProcessorService.processHydroParametersMeDirectory(trajectoryToUse, horizon, studyId)), HttpStatus.CREATED);
    }

}
