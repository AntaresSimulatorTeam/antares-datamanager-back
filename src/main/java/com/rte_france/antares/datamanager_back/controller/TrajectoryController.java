package com.rte_france.antares.datamanager_back.controller;


import com.rte_france.antares.datamanager_back.dto.Type;
import com.rte_france.antares.datamanager_back.service.AreaFileProcessorService;
import com.rte_france.antares.datamanager_back.service.LinkFileProcessorService;
import com.rte_france.antares.datamanager_back.service.TrajectoryProcessorService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;


@Slf4j
@RestController
@RequestMapping("/v1/trajectory")
@RequiredArgsConstructor
public class TrajectoryController {

    private final AreaFileProcessorService areaFileProcessorService;

    private final LinkFileProcessorService linkFileProcessorService;

    private final TrajectoryProcessorService trajectoryProcessorService;

    @Operation(summary = "import Trajectory file to database ")
    @PostMapping
    public ResponseEntity uploadArea(@RequestParam("trajectoryType") Type trajectoryType,
                                     @RequestParam("trajectoryToUse") String trajectoryToUse) throws IOException {
        trajectoryProcessorService.processTrajectory(trajectoryType, trajectoryToUse);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }


}
