package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.configuration.gaia.Employee;
import com.rte_france.antares.datamanager_back.service.impl.LdapClientEmployeeService;
import com.rte_france.antares.datamanager_back.dto.FsTrajectoryDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryDataDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
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
                                                                            @RequestParam(value = "fileNameContains", required = false) String fileNameContains,
                                                                            @RequestParam(value = "loadArea", required = false) String loadArea) {


        return new ResponseEntity<>(toTrajectoryDtos(trajectoryService.findTrajectoriesByTypeAndFileNameContainsFromDB(trajectoryType, horizon, fileNameContains, loadArea)), HttpStatus.OK);
    }


    @Operation(summary = "Get Trajectories by type and fileNameContains from File System")
    @GetMapping(value = "/fs")
    public ResponseEntity<List<FsTrajectoryDTO>> findTrajectoriesByTypeFromFileSystem(
            @RequestParam("trajectoryType") TrajectoryType trajectoryType,
            @Parameter(description = "parameter to use just in load case")
            @RequestParam(value = "zone", required = false)
            @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid area name") String loadZone,
            @RequestParam(value = "fileNameContains", required = false) String fileNameContains) throws TechnicalException {
        return ResponseEntity.ok(trajectoryService.findTrajectoriesByType(trajectoryType, fileNameContains));
    }


    @Operation(summary = "import Trajectory file to database ")
    @PostMapping
    public ResponseEntity<TrajectoryDTO> uploadTrajectory(@RequestParam("trajectoryType") TrajectoryType trajectoryType,
                                                          @RequestParam("trajectoryToUse")
                                                          @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid trajectory file name")
                                                          String trajectoryToUse,
                                                          @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$")
                                                          @Parameter(description = "example of horizon : 2020-2021") String horizon,
                                                          @RequestParam("studyId") Integer studyId) throws IOException {
        return new ResponseEntity<>(toTrajectoryDTO(trajectoryService.processTrajectory(trajectoryType, trajectoryToUse, horizon, studyId)), HttpStatus.CREATED);
    }

    @Operation(summary = "import Trajectory load to database ")
    @PostMapping("/load")
    public ResponseEntity<TrajectoryDTO> uploadTrajectory(@RequestParam("area") String area,
                                                          @RequestParam("trajectoryToUse") String trajectoryToUse,
                                                          @RequestParam("horizon") @Pattern(regexp = "^\\d{4}-\\d{4}$")
                                                          @Parameter(description = "example of horizon : 2020-2021") String horizon,
                                                          @RequestParam("studyId") Integer studyId) throws IOException {
        return new ResponseEntity<>(toTrajectoryDTO(trajectoryService.processLoadTrajectory(area, trajectoryToUse, horizon, studyId)), HttpStatus.CREATED);
    }

    @GetMapping
    public List<TrajectoryDTO> getTrajectoriesByStudyIdAndType(@RequestParam("studyId") Integer studyId,
                                                               @RequestParam(value = "trajectoryType", required = false) TrajectoryType trajectoryType) {
        return trajectoryService.findTrajectoriesByTypeAndStudyId(trajectoryType != null ? trajectoryType.name() : null, studyId);
    }


    @PutMapping("/link")
    public ResponseEntity<TrajectoryDTO> linkTrajectoryToStudy(@RequestParam TrajectoryType type,
                                                               @RequestParam Integer trajectoryId,
                                                               @RequestParam Integer studyId) throws IOException {
        return new ResponseEntity<>(toTrajectoryDTO(trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, type)), HttpStatus.OK);
    }

    @DeleteMapping("/link")
    public ResponseEntity<Void> unlinkTrajectoryFromStudy(@RequestParam Integer trajectoryId,
                                                          @RequestParam Integer studyId) {
        trajectoryService.unlinkTrajectoryFromStudy(trajectoryId, studyId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Operation(summary = "Get trajectory data")
    @GetMapping(value = "/trajectoryData")
    public ResponseEntity<List<TrajectoryDataDTO>> findTrajectoriesByTypeFromFileSystem(
            @RequestParam("trajectoryType") TrajectoryType trajectoryType,
            @RequestParam(value = "trajectoryId") Integer trajectoryId) {

        return ResponseEntity.ok(trajectoryService.getTrajectoryDataByTypeAndId(trajectoryType, trajectoryId));
    }
}
