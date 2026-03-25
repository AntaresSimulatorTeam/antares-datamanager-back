package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.dto.TrajectoryDTO;
import com.rte_france.antares.datamanager_back.repository.model.ResTypeEntity;
import com.rte_france.antares.datamanager_back.service.res.ResFileProcessorService;
import com.rte_france.antares.datamanager_back.service.res.ResTypeService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.util.List;

import static com.rte_france.antares.datamanager_back.mapper.TrajectoryMapper.toTrajectoryDTO;

@Slf4j
@RestController
@Validated
@RequestMapping("/v1/trajectory")
@RequiredArgsConstructor
public class ResController {

    private final ResTypeService resTypeService;
    private final ResFileProcessorService resFileProcessorService;


    @GetMapping("res-types")
    public ResponseEntity<List<ResTypeEntity>> getAllResTypes() {
        return ResponseEntity.ok(resTypeService.getAllResTypes());
    }

    @Operation(summary = "import load factor RES trajectory to database ")
    @PostMapping("/load-factor-res")
    public ResponseEntity<TrajectoryDTO> uploadLoadFactorResTrajectory(
            @RequestParam("area") String area,
            @RequestParam("trajectoryToUse") @Size(max = 40, message = "Trajectory name cannot exceed 40 characters") @Pattern(regexp = "^[a-zA-Z0-9_-]+$") String trajectoryToUse,
            @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$") String horizon,
            @RequestParam("studyId") Integer studyId,
            @RequestParam("technology") @Pattern(regexp = "^[a-zA-Z0-9_-]+$") String technology) throws Exception {

        return new ResponseEntity<>(toTrajectoryDTO(resFileProcessorService.processLoadFactorResFile(trajectoryToUse, horizon, studyId, area, technology)), HttpStatus.CREATED);
    }

    @Operation(summary = "import installed RES trajectory to database ")
    @PostMapping("/installed-power-res")
    public ResponseEntity<TrajectoryDTO> uploadInstalledResTrajectory(
            @RequestParam("area") String area,
            @RequestParam(value = "technology", required = false) String technology,
            @RequestParam("trajectoryToUse") @Size(max = 40, message = "Trajectory name cannot exceed 40 characters") @Pattern(regexp = "^[a-zA-Z0-9_-]+$") String trajectoryToUse,
            @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$") String horizon,
            @RequestParam("studyId") Integer studyId,
            @RequestParam("isCivilYear") boolean isCivilYear) throws IOException {

        var result = resFileProcessorService.processInstalledResFile(trajectoryToUse, horizon, studyId, area, technology, isCivilYear);

        return new ResponseEntity<>(toTrajectoryDTO(result), HttpStatus.CREATED);
    }
}

