package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.dto.TrajectoryDTO;
import com.rte_france.antares.datamanager_back.service.settings.SettingsImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Trajectory Settings", description = "APIs for importing trajectory settings from Excel files")
public class SettingsController {

    private final SettingsImportService trajectorySettingsImportService;

    @Operation(summary = "Import trajectory settings from Excel file (general_data_*.xlsx)")
    @PostMapping("/settings")
    public ResponseEntity<TrajectoryDTO> importTrajectorySettings(
            @RequestParam("trajectoryToUse") 
            @Size(max = 40, message = "Trajectory name cannot exceed 40 characters") 
            @Pattern(regexp = "^[a-zA-Z0-9_-]+$") 
            @Parameter(description = "Name of the trajectory folder (e.g., BP23_A_ref_200MC)") 
            String trajectoryToUse,
            @RequestParam("horizon") 
            @Pattern(regexp = "^\\d{4}-\\d{4}$") 
            @Parameter(description = "Horizon in format YYYY-YYYY (e.g., 2028-2029)") 
            String horizon,
            @RequestParam("studyId") 
            @Parameter(description = "Study ID") 
            Integer studyId,
            @RequestParam("area") 
            @Size(max = 40, message = "Area name cannot exceed 40 characters") 
            @Pattern(regexp = "^[a-zA-Z0-9_-]+$") 
            @Parameter(description = "Area code (e.g., FR, DE, IT)") 
            String area) throws IOException {

        log.info("Importing trajectory settings for: trajectory={}, horizon={}, studyId={}, area={}", 
                trajectoryToUse, horizon, studyId, area);

        return new ResponseEntity<>(toTrajectoryDTO(
                trajectorySettingsImportService.importSettings(trajectoryToUse, horizon, studyId, area)
        ), HttpStatus.CREATED);
    }
}
