package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.LoadRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.LoadEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.LoadFileProcessorService;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesReader;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.rte_france.antares.datamanager_back.util.Utils.buildTrajectory;
import static com.rte_france.antares.datamanager_back.util.Utils.checkTrajectoryVersion;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoadFileProcessorServiceImpl implements LoadFileProcessorService {
  private final TrajectoryRepository trajectoryRepository;
  private final NasFileService nasFileService;
  private final TimeSeriesReader reader;
  private final TimeSeriesWriter writer;
  private final LoadRepository loadRepository;
  private final UserService userService;

  /**
   * Processes the given file.
   * If a trajectory with the same file name exists, it updates the trajectory.
   * Otherwise, it creates a new trajectory.
   * Also reads the txt matrix and saves it to the NAS as a compressed .arrow file
   *
   * @param path the path to the file to process
   */
  @Transactional
  public TrajectoryEntity processLoadFile(Path path, String horizon) throws IOException {
    Objects.requireNonNull(path);
    Objects.requireNonNull(horizon);

    Optional<TrajectoryEntity> trajectoryEntity = trajectoryRepository.findFirstByFileNameOrderByVersionDesc(path.getFileName().toString());
    String createdBy = userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : "UNKNOWEN__USER";

    TrajectoryEntity savedTrajectory;
    if (trajectoryEntity.isPresent() && checkTrajectoryVersion(path, trajectoryEntity.get())) {
      savedTrajectory = saveTrajectory(buildTrajectory(path, trajectoryEntity.get().getVersion(),horizon, createdBy));
    } else {
      savedTrajectory = saveTrajectory(buildTrajectory(path, 0,horizon, createdBy));
    }

    saveMatrixToNas(path);

    return savedTrajectory;
  }

  private TrajectoryEntity saveTrajectory(TrajectoryEntity trajectory) {
    var trajectoryEntity = trajectoryRepository.save(trajectory);
    trajectory.setType(TrajectoryType.LOAD.name());
    var loadEntity = LoadEntity.builder().trajectory(trajectoryEntity).build();
    trajectory.setLoadEntity(loadEntity);
    loadRepository.save(loadEntity);

    return trajectoryEntity;
  }

  private void saveMatrixToNas(Path path) throws IOException {
    var matrix = reader.readFromTxt(path);
    var outputFilePath = path.resolveSibling(path.getFileName() + "." + writer.getDefaultFileExtension());
    var matrixAsBytes = writer.writeToByteArray(matrix);
    nasFileService.saveFile(outputFilePath.getFileName().toString(), matrixAsBytes);
    var permissions = PosixFilePermissions.fromString("rw-------");
    Files.setPosixFilePermissions(path, permissions);
  }
}
