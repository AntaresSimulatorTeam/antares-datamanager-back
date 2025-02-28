package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.LoadRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.AreaConfigEntity;
import com.rte_france.antares.datamanager_back.repository.model.LoadEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.LoadFileProcessorService;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrix;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesReader;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.rte_france.antares.datamanager_back.util.Utils.*;
import static com.rte_france.antares.datamanager_back.util.Utils.buildTrajectory;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoadFileProcessorServiceImpl implements LoadFileProcessorService {
  private final TrajectoryRepository trajectoryRepository;
  private final NasFileService nasFileService;
  private final TimeSeriesReader reader;
  private final TimeSeriesWriter writer;
  private final LoadRepository loadRepository;

  public TrajectoryEntity processLoadFile(Path path, String horizon) throws IOException {
    Objects.requireNonNull(path);
    Objects.requireNonNull(horizon);

    Optional<TrajectoryEntity> trajectoryEntity = trajectoryRepository.findFirstByFileNameOrderByVersionDesc(path.getFileName().toString());

    TrajectoryEntity savedTrajectory;
    if (trajectoryEntity.isPresent() && checkTrajectoryVersion(path, trajectoryEntity.get())) {
      savedTrajectory = saveTrajectory(buildTrajectory(path, trajectoryEntity.get().getVersion(),horizon));
    } else {
      savedTrajectory = saveTrajectory(buildTrajectory(path, 0,horizon));
    }

    var matrix = reader.read(path);
    var outputFilePath = path.resolveSibling(path.getFileName() + "-output");
    writer.write(matrix, outputFilePath);
    nasFileService.saveFile(outputFilePath.toString(), Files.readAllBytes(outputFilePath));

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

//  public TrajectoryEntity retrieveLoadFileFromFS(String fileName) throws IOException {
//    var filePath = Path.of(nasFileService.loadFile(fileName).getURI());
//    var matrix = reader.read(filePath);
//
//    var trajectoryEntity = new TrajectoryEntity();
//    trajectoryEntity.setFileName(filePath.getFileName().toString());
//    trajectoryEntity = trajectoryRepository.save(trajectoryEntity);
//
//    var loadEntity = new LoadEntity();
//    loadEntity.setTrajectory(trajectoryEntity);
//
//    return trajectoryRepository.save(trajectoryEntity);
//  }

  public TimeSeriesMatrix readTimeSeries(Path filePath) throws IOException {
    return reader.read(filePath);
  }

  public void writeTimeSeries(TimeSeriesMatrix matrix, Path outputPath) throws IOException {
    writer.write(matrix, outputPath);
  }
}
