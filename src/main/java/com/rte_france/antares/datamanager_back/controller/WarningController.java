package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.WarningDTO;
import com.rte_france.antares.datamanager_back.service.WarningService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/v1/warnings")
@RequiredArgsConstructor
public class WarningController {

    private final WarningService warningService;

    @PutMapping("/{id}/ack")
    public ResponseEntity<Void> acknowledgeWarning(@PathVariable Integer id) {
        warningService.acknowledgeWarning(id);
        return ResponseEntity.ok().build();
    }


    @Operation(summary = "Get warnings by trajectoryId and studyId")
    @GetMapping
    public ResponseEntity<Set<WarningDTO>> fetchWarningByTrajectoryId(
            @RequestParam(value = "trajectoryType") TrajectoryType trajectoryType,
            @RequestParam(value = "studyId") Integer studyId) {
        return ResponseEntity.ok(warningService.getWarningsForTrajectory(studyId, trajectoryType));
    }

}