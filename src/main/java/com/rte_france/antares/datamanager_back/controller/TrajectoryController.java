package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.dto.FsTrajectoryDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.service.TrajectoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Pattern;
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

    @Operation(summary = "Get Trajectories by type and fileNameContains from Database ")
    @GetMapping(value = "/db")
    public ResponseEntity<List<TrajectoryDTO>> findTrajectoriesByTypeFromDb(@RequestParam("trajectoryType") TrajectoryType trajectoryType,
                                                                            @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$")
                                                                            @Parameter(description = "example of horizon : 2020-2021") String horizon,
                                                                            @RequestParam(value = "fileNameContains", required = false) String fileNameContains) {
        return new ResponseEntity<>(toTrajectoryDtos(trajectoryService.findTrajectoriesByTypeAndFileNameContainsFromDB(trajectoryType, horizon, fileNameContains)), HttpStatus.OK);
    }


    @Operation(summary = "Get Trajectories by type and fileNameContains from File System")
    @GetMapping(value = "/fs")
    public ResponseEntity<List<FsTrajectoryDTO>> findTrajectoriesByTypeFromFileSystem(@RequestParam("trajectoryType") TrajectoryType trajectoryType,
                                                                                      @Parameter(description = "parameter to user just in thermal capacity case")
                                                                                      @RequestParam(value = "thermalCapacityArea", required = false) String thermalCapacityArea) {
        return new ResponseEntity<>(trajectoryService.findTrajectoriesByType(trajectoryType), HttpStatus.OK);
    }

    @Operation(summary = "import Trajectory file to database ")
    @PostMapping
    public ResponseEntity<TrajectoryDTO> uploadTrajectory(@RequestParam("trajectoryType") TrajectoryType trajectoryType,
                                                          @RequestParam("trajectoryToUse") String trajectoryToUse,
                                                          @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$")
                                                          @Parameter(description = "example of horizon : 2020-2021") String horizon)
            throws IOException {
        return new ResponseEntity<>(toTrajectoryDTO(trajectoryService.processTrajectory(trajectoryType, trajectoryToUse, horizon)), HttpStatus.CREATED);
    }

    @GetMapping
    public List<TrajectoryDTO> getTrajectoriesByStudyIdAndType(@RequestParam("studyId") Integer studyId,
                                                               @RequestParam("trajectoryType") String trajectoryType) {
        return trajectoryService.findTrajectoriesByTypeAndStudyId(trajectoryType, studyId);
    }


    @PutMapping("/link")
    public ResponseEntity<TrajectoryDTO> linkTrajectoryToStudy(@RequestParam TrajectoryType type,
                                                               @RequestParam Integer trajectoryId,
                                                               @RequestParam Integer studyId) {
        return new ResponseEntity<>(toTrajectoryDTO(trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, type)), HttpStatus.OK);
    }

    @DeleteMapping("/link")
    public ResponseEntity<Void> unlinkTrajectoryFromStudy(@RequestParam Integer trajectoryId,
                                                          @RequestParam Integer studyId) {
        trajectoryService.unlinkTrajectoryFromStudy(trajectoryId, studyId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

}
