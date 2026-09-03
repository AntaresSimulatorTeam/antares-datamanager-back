package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.dto.*;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.service.common.TrajectoryService;
import com.rte_france.antares.datamanager_back.util.PathSecurityUtil;
import com.rte_france.antares.datamanager_back.validation.ValidTrajectoryName;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.rte_france.antares.datamanager_back.mapper.TrajectoryMapper.toTrajectoryDTO;
import static com.rte_france.antares.datamanager_back.mapper.TrajectoryMapper.toTrajectoryDtos;


@Slf4j
@RestController
@Validated
@RequestMapping("/v1/trajectory")
@RequiredArgsConstructor
public class TrajectoryController {

    private final TrajectoryService trajectoryService;
    private final PathSecurityUtil pathSecurityUtil;

    @Operation(summary = "Get Trajectories by type and fileNameContains from Database ")
    @GetMapping(value = "/db")
    public ResponseEntity<List<TrajectoryDTO>> findTrajectoriesByTypeFromDb(@RequestParam("trajectoryType") TrajectoryType trajectoryType,
                                                                            @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$")
                                                                            @Parameter(description = "example of horizon : 2020-2021") String horizon,
                                                                            @RequestParam(value = "fileNameContains", required = false) String fileNameContains,
                                                                            @RequestParam(value = "area", required = false) String area,
                                                                            @RequestParam(value = "technology", required = false) String technology) {


        return new ResponseEntity<>(toTrajectoryDtos(
                trajectoryService.findTrajectoriesByTypeAndFileNameContainsFromDB(trajectoryType, horizon, fileNameContains, area, technology)),
                HttpStatus.OK);
    }


    @Operation(summary = "Get Trajectories by type and fileNameContains from File System")
    @GetMapping(value = "/fs")
    public ResponseEntity<List<FsTrajectoryDTO>> findTrajectoriesByTypeFromFileSystem(
            @RequestParam("trajectoryType") TrajectoryType trajectoryType,
            @RequestParam(value = "technology", required = false) String technology,
            @RequestParam(value = "area", required = false) String area,
            @RequestParam(value = "fileNameContains", required = false) String fileNameContains) throws TechnicalException, IOException {
        return ResponseEntity.ok(trajectoryService.findTrajectoriesByType(trajectoryType, area,technology, fileNameContains));
    }


    @Operation(summary = "import Trajectory [area , area multi energy, link, link multi energy] file to database ")
    @PostMapping
    public ResponseEntity<TrajectoryDTO> uploadTrajectory(@RequestParam("trajectoryType") TrajectoryType trajectoryType,
                                                          @RequestParam("trajectoryToUse") @ValidTrajectoryName
                                                          String trajectoryToUse,
                                                          @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$")
                                                          @Parameter(description = "example of horizon : 2020-2021") String horizon,
                                                          @RequestParam("studyId") Integer studyId
                                                          ) throws IOException {
        pathSecurityUtil.resolveSafePath(
                properties -> Path.of(properties.getNasDirectory(), properties.getTrajectoryFilePath()),
                trajectoryToUse
        );
        return new ResponseEntity<>(toTrajectoryDTO(trajectoryService.processTrajectory(trajectoryType, trajectoryToUse, horizon, studyId)), HttpStatus.CREATED);
    }

    @Operation(summary = "import Trajectory load to database ")
    @PostMapping("/load")
    public ResponseEntity<TrajectoryDTO> uploadTrajectory(@RequestParam("area") String area,
                                                          @RequestParam("trajectoryToUse") @ValidTrajectoryName String trajectoryToUse,
                                                          @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$")
                                                          @Parameter(description = "example of horizon : 2020-2021") String horizon,
                                                          @RequestParam("studyId") Integer studyId) throws IOException {
        pathSecurityUtil.resolveSafePath(
                properties -> Path.of(properties.getNasDirectory(), properties.getTrajectoryFilePath()),
                trajectoryToUse
        );
        return new ResponseEntity<>(toTrajectoryDTO(trajectoryService.processLoadTrajectory(area, trajectoryToUse, horizon, studyId)), HttpStatus.CREATED);
    }

    @GetMapping
    public List<TrajectoryDTO> getTrajectoriesByStudyIdAndType(@RequestParam("studyId") Integer studyId,
                                                               @RequestParam(value = "trajectoryType", required = false) TrajectoryType trajectoryType) {
        return trajectoryService.findTrajectoriesByTypeAndStudyId(trajectoryType != null ? trajectoryType.name() : null, studyId);
    }

    @Operation(summary = "Attach trajectory data to study ",
            description = "Maps trajectory to study")
    @PutMapping("/attach")
    public ResponseEntity<TrajectoryDTO> attachTrajectoryToStudy(@RequestParam TrajectoryType type,
                                                                 @RequestParam Integer trajectoryId,
                                                                 @RequestParam Integer studyId) throws IOException {
        return new ResponseEntity<>(toTrajectoryDTO(trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, type)), HttpStatus.OK);
    }

    @Operation(
            summary = "Detach trajectory from study",
            description = "Removes mapping between a trajectory and a study"
    )

    @DeleteMapping("/detach")
    public ResponseEntity<Void> unlinkTrajectoryFromStudy(@RequestParam Integer trajectoryId,
                                                          @RequestParam Integer studyId) {
        trajectoryService.unlinkTrajectoryFromStudy(trajectoryId, studyId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Operation(summary = "Detach multiple trajectories from a study")
    @PostMapping("/detach/batch")
    public ResponseEntity<Void> unlinkBatch(@RequestParam Integer studyId,
                                            @RequestBody List<Integer> trajectoryIds) {
        trajectoryService.unlinkBatchTrajectoriesFromStudy(studyId, trajectoryIds);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/detach/all")
    public ResponseEntity<Void> unlinkAllTrajectoriesFromStudy(@RequestParam Integer studyId) {
        trajectoryService.unlinkAllTrajectoriesFromStudy(studyId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Operation(summary = "Get trajectory data")
    @GetMapping(value = "/trajectoryData")
    public ResponseEntity<List<TrajectoryDataDTO>> getTrajectoryDataByTypeAndId(
            @RequestParam("trajectoryType") TrajectoryType trajectoryType,
            @RequestParam(value = "trajectoryId") Integer trajectoryId) {

        return ResponseEntity.ok(trajectoryService.getTrajectoryDataByTypeAndId(trajectoryType, trajectoryId));
    }

    @Operation(summary = "Get warning message number for each trajectory type of a study")
    @GetMapping("/count/warning/{id}")
    public ResponseEntity<Map<String, Integer>> countWarningMessage(@PathVariable Integer id) {
        return new ResponseEntity<>(trajectoryService.countWarningMessage(id), HttpStatus.OK);
    }
}
