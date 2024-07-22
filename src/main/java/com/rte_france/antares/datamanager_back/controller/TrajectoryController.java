package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.dto.TrajectoryDTO;
import com.rte_france.antares.datamanager_back.dto.Type;
import com.rte_france.antares.datamanager_back.service.TrajectoryService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

import static com.rte_france.antares.datamanager_back.mapper.TrajectoryMapper.toTrajectoryDTO;
import static com.rte_france.antares.datamanager_back.mapper.TrajectoryMapper.toTrajectoryDtos;


@Slf4j
@RestController
@RequestMapping("/v1/trajectory")
@RequiredArgsConstructor
public class TrajectoryController {

    private final TrajectoryService trajectoryService;

    @Operation(summary = "import Trajectory file to database ")
    @PostMapping
    public ResponseEntity<TrajectoryDTO> uploadTrajectory(@RequestParam("trajectoryType") Type trajectoryType,
                                                          @RequestParam("trajectoryToUse") String trajectoryToUse) throws IOException {
        return new ResponseEntity<>(toTrajectoryDTO(trajectoryService.processTrajectory(trajectoryType, trajectoryToUse)), HttpStatus.CREATED);
    }

    @Operation(summary = "Get Trajectories by type and fileNameStartsWith from Database ")
    @GetMapping(value = "/db")
    public ResponseEntity<List<TrajectoryDTO>> findTrajectoriesByTypeFromDb(@RequestParam("trajectoryType") Type trajectoryType,
                                                                            @RequestParam("fileNameStartsWith") String fileNameStartsWith) {
        return new ResponseEntity<>(toTrajectoryDtos(trajectoryService.findTrajectoriesByTypeAndFileNameStartWithFromDB(trajectoryType, fileNameStartsWith)), HttpStatus.OK);
    }


    @Operation(summary = "Get Trajectories by type and fileNameStartsWith from File System")
    @GetMapping(value = "/fs")
    public ResponseEntity<List<String>> findTrajectoriesByTypeFromFileSystem(@RequestParam("trajectoryType") Type trajectoryType) {
        return new ResponseEntity<>(trajectoryService.findTrajectoriesByTypeAndFileNameStartWithFromFS(trajectoryType), HttpStatus.OK);
    }

}
