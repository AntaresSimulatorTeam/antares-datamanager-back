package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.dto.TrajectoryDTO;
import com.rte_france.antares.datamanager_back.service.common.TrajectoryService;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalSpecificFileProcessorService;
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

import static com.rte_france.antares.datamanager_back.mapper.TrajectoryMapper.toTrajectoryDTO;

@Slf4j
@RestController
@Validated
@RequestMapping("/v1/trajectory")
@RequiredArgsConstructor
public class ThermalController {
    private final TrajectoryService trajectoryService;
    private final ThermalSpecificFileProcessorService thermalSpecificFileProcessorService;

    @Operation(summary = "import thermal capacity trajectory to database ")
    @PostMapping("/thermal-capacity")
    public ResponseEntity<TrajectoryDTO> uploadThermalCapacityTrajectory(@RequestParam("area") String area, // FR, // GB, DE, IT, ES, PT, BE, NL, LU, CH //OTHER
                                                                         @RequestParam(value = "technology", required = false) String technology,
                                                                         @RequestParam("trajectoryToUse") @Size(max = 40, message = "Trajectory name cannot exceed 40 characters") String trajectoryToUse,
                                                                         @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$")
                                                                         @Parameter(description = "example of horizon : 2020-2021") String horizon,
                                                                         @RequestParam("studyId") Integer studyId,
                                                                         @RequestParam("isCivilYear") boolean isCivilYear) throws Exception {

        return new ResponseEntity<>(toTrajectoryDTO(
                trajectoryService.processThermalCapacityTrajectory(trajectoryToUse, horizon, studyId, isCivilYear, area, technology)
        ), HttpStatus.CREATED);
    }

    @Operation(summary = "import thermal common parameters trajectory to database ")
    @PostMapping("/thermal-common-parameter")
    public ResponseEntity<TrajectoryDTO> uploadThermalParameterTrajectory(
            @RequestParam("trajectoryToUse") @Size(max = 40, message = "Trajectory name cannot exceed 40 characters") String trajectoryToUse,
            @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$")
            @Parameter(description = "example of horizon : 2020-2021") String horizon,
            @RequestParam("studyId") Integer studyId) throws Exception {
        return new ResponseEntity<>(toTrajectoryDTO(
                trajectoryService.processThermalCommonParameterTrajectory(trajectoryToUse, horizon, studyId)
        ), HttpStatus.CREATED);
    }


    @Operation(summary = "import thermal specific parameters trajectory to database ")
    @PostMapping("/thermal-specific-parameter")
    public ResponseEntity<TrajectoryDTO> uploadThermalSpecificParameterTrajectory(
            @RequestParam("area") String area,
            @RequestParam("trajectoryToUse") @Size(max = 40, message = "Trajectory name cannot exceed 40 characters") String trajectoryToUse,
            @RequestParam("horizon") String horizon,
            @RequestParam("studyId") Integer studyId) throws Exception {
        return new ResponseEntity<>(toTrajectoryDTO(
                trajectoryService.processThermalSpecificParameterTrajectory(trajectoryToUse, horizon, area, studyId)
        ), HttpStatus.CREATED);
    }

    @Operation(summary = "import thermal modulation parameters trajectory to database ")
    @PostMapping("/thermal-modulation-parameter")
    public ResponseEntity<TrajectoryDTO> uploadThermalModulationParameterTrajectory(
            @RequestParam("trajectoryToUse") @Size(max = 40, message = "Trajectory name cannot exceed 40 characters") String trajectoryToUse,
            @RequestParam("horizon") String horizon,
            @RequestParam("studyId") Integer studyId) throws Exception {
        return new ResponseEntity<>(toTrajectoryDTO(
                trajectoryService.processThermalModulationParameterTrajectory(trajectoryToUse, horizon, studyId)
        ), HttpStatus.CREATED);
    }

    @Operation(summary = "import thermal economic costs trajectory to database ")
    @PostMapping("/thermal-economic-costs")
    public ResponseEntity<TrajectoryDTO> uploadThermalEconomicCostsTrajectory(
            @RequestParam("trajectoryToUse") @Size(max = 40, message = "Trajectory name cannot exceed 40 characters") String trajectoryToUse,
            @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$")
            @Parameter(description = "example of horizon : 2020-2021") String horizon,
            @RequestParam("studyId") Integer studyId) throws Exception {
        return new ResponseEntity<>(toTrajectoryDTO(
                trajectoryService.processThermalEconomicCostTrajectory(trajectoryToUse, horizon, studyId)), HttpStatus.CREATED);
    }


    @Operation(summary = "import thermal economic parameters trajectory to database ")
    @PostMapping("/thermal-economic-parameter")
    public ResponseEntity<TrajectoryDTO> uploadThermalEconomicParamTrajectory(
            @RequestParam("trajectoryToUse") @Size(max = 40, message = "Trajectory name cannot exceed 40 characters") String trajectoryToUse,
            @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$")
            @Parameter(description = "example of horizon : 2020-2021") String horizon,
            @RequestParam("studyId") Integer studyId) throws Exception {
        return new ResponseEntity<>(toTrajectoryDTO(trajectoryService.processThermalEconomicParameterTrajectory(trajectoryToUse, horizon, studyId)
        ), HttpStatus.CREATED);
    }

    @PostMapping("/param-modulation/check")
    public ResponseEntity<Boolean> checkParamModulationRequired(
            @RequestParam("horizon")
            @Pattern(regexp = "^\\d{4}-\\d{4}$", message = "Horizon must match format YYYY-YYYY")
            @Parameter(description = "example of horizon : 2020-2021")
            String horizon,

            @RequestParam("studyId")
            @Parameter(description = "ID of the study")
            Integer studyId
    ) {
        return ResponseEntity.ok(thermalSpecificFileProcessorService.isParamModulationRequired(horizon, studyId));
    }

}
