package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.dto.TrajectoryDTO;
import com.rte_france.antares.datamanager_back.service.sts.StStorageFileProcessorService;
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
public class StStorageController {

    private final StStorageFileProcessorService stStorageFileProcessorService;

    @Operation(summary = "import sts trajectory to database ")
    @PostMapping("/st-storage")
    public ResponseEntity<TrajectoryDTO> uploadStStorageTrajectory(@RequestParam("area") String area, // FR, // GB, DE, IT, ES, PT, BE, NL, LU, CH //OTHER
                                                                         @RequestParam(value = "technology") String technology,
                                                                         @RequestParam("trajectoryToUse") @Size(max = 40, message = "Trajectory name cannot exceed 40 characters") String trajectoryToUse,
                                                                         @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$") @Parameter(description = "example of horizon : 2020-2021") String horizon,
                                                                         @RequestParam("studyId") Integer studyId, @RequestParam("isCivilYear") boolean isCivilYear) throws Exception {

        return new ResponseEntity<>(toTrajectoryDTO
                (stStorageFileProcessorService.processStStorageFile(trajectoryToUse, horizon, studyId, isCivilYear, area, technology)),
                HttpStatus.CREATED);
    }
}
