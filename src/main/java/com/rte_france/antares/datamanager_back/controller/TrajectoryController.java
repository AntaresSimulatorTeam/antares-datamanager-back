package com.rte_france.antares.datamanager_back.controller;


import com.rte_france.antares.datamanager_back.dto.Type;
import com.rte_france.antares.datamanager_back.service.TrajectoryProcessorService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;


@Slf4j
@RestController
@RequestMapping("/v1/trajectory")
@RequiredArgsConstructor
public class TrajectoryController {


    private final TrajectoryProcessorService trajectoryProcessorService;

    @Operation(summary = "import Trajectory file to database ")
    @PostMapping
    public ResponseEntity uploadTrajectory(@RequestParam("trajectoryType") Type trajectoryType,
                                           @RequestParam("trajectoryToUse") String trajectoryToUse) throws IOException {
        trajectoryProcessorService.processTrajectory(trajectoryType, trajectoryToUse);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }


    @Operation(summary = "Get Trajectories by type ")
    @GetMapping
    public ResponseEntity<List<String>> findTrajectoriesByType(@RequestParam("trajectoryType") Type trajectoryType)

    {
        //TODO
        //check if trajectory exist in DB else scan appropriate directory
        return new ResponseEntity<>(trajectoryProcessorService.findTrajectoriesByType(trajectoryType), HttpStatus.OK);
    }


}
