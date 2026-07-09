package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.dto.TrajectoryDTO;
import com.rte_france.antares.datamanager_back.service.adequacy.AdequacyFileProcessorService;
import com.rte_france.antares.datamanager_back.service.dsr.DsrFileProcessorService;
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
public class AdequacyController {

    private final AdequacyFileProcessorService adequacyFileProcessorService;


    @Operation(summary = "import adequacy patch trajectory to database ")
    @PostMapping("/adequacy-patch")
    public ResponseEntity<TrajectoryDTO> uploadAdequacyTrajectory(@RequestParam("trajectoryToUse") @Size(max = 40, message = "Trajectory name cannot exceed 40 characters") String trajectoryToUse,
                                                                    @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$") String horizon,
                                                                    @RequestParam("studyId") Integer studyId,
                                                                    @RequestParam("isCivilYear") boolean isCivilYear) throws IOException {

        return new ResponseEntity<>(toTrajectoryDTO(
                adequacyFileProcessorService.processAdequacyFile(trajectoryToUse, horizon, studyId, isCivilYear)
        ), HttpStatus.CREATED);
    }
}
