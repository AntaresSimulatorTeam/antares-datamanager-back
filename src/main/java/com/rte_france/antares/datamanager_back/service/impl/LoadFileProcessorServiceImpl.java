package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.WarningCode;
import com.rte_france.antares.datamanager_back.repository.model.WarningMessageEntity;
import com.rte_france.antares.datamanager_back.service.LoadFileProcessorService;
import com.rte_france.antares.datamanager_back.service.WarningMessageService;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrix;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesReader;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import java.util.*;

import static org.apache.xmlbeans.impl.schema.StscState.addWarning;


@Slf4j
@Service
@RequiredArgsConstructor
public class LoadFileProcessorServiceImpl implements LoadFileProcessorService {
  private final NasFileService nasFileService;
  private final TimeSeriesReader reader;
  private final TimeSeriesWriter writer;
  private final TrajectoryRepository trajectoryRepository;
  private final AreaRepository areaRepository;
  private final WarningMessageService warningMessageService;

  /**
   *
   * @param inputPath
   * @return
   * @throws IOException
   */

  public String saveMatrixToNas(Path inputPath) throws IOException {
    var matrix = reader.readFromTxt(inputPath);
    var outputFileName = generateUniqueFileName(inputPath);

    saveMatrix(outputFileName, matrix);
    setFilePermissions(inputPath);

    return outputFileName;
  }

  private String generateUniqueFileName(Path inputPath) {
    String baseName = inputPath.getFileName().toString();
    String extension = writer.getDefaultFileExtension();
    String uuid = UUID.randomUUID().toString();
    return baseName + "." + uuid + "." + extension;
  }

  private void saveMatrix(String fileName, TimeSeriesMatrix matrix) throws IOException {
    byte[] data = writer.writeToByteArray(matrix);
    nasFileService.saveFile(fileName, data);
  }

  private void setFilePermissions(Path path) throws IOException {
    var permissions = PosixFilePermissions.fromString("rw-------");
    Files.setPosixFilePermissions(path, permissions);
  }

  public Set<WarningMessageEntity> checkForMissingLoadFiles(Path trajectoryPath, String horizon, Integer studyId, String userNni, TrajectoryEntity trajectory)

  {
    List<String> studyAreas = areaRepository.findAllByStudyId(studyId)
            .stream()
            .map(AreaEntity::getName)
            .toList();

    Set<WarningMessageEntity> warningMessages = new HashSet<>();


    List<String> areasWithTrajectory = trajectoryRepository.findByTypeAndStudyId(TrajectoryType.LOAD.name(), studyId)
            .stream()
            .map(TrajectoryEntity::getLoadArea)
            .toList();

    List<String> areasWithoutTrajectory = studyAreas.stream()
            .filter(area -> !areasWithTrajectory.contains(area))
            .toList();


    List<String> missingLoadFiles = areasWithoutTrajectory.stream()
            .filter(area -> {
              Path loadFile = trajectoryPath.resolve("load_" + area.toLowerCase() + "_" + horizon + ".txt");
              return !Files.exists(loadFile);
            })
            .toList();
    if (!areasWithoutTrajectory.isEmpty() && missingLoadFiles.size() == areasWithoutTrajectory.size()) {
      throw BusinessException.builder()
              .message("Missing file(s) for area(s) {0} in LOAD Other areas trajectory {1}")
              .errorMessageArguments(Arrays.asList(
                      String.join(", ", areasWithoutTrajectory),
                      trajectory.getFileName()
              ))
              .httpStatus(HttpStatus.BAD_REQUEST)
              .build();
    }


    if (!missingLoadFiles.isEmpty()) {
      warningMessageService.addWarning(warningMessages,
              Arrays.asList(
                      String.join(", ", missingLoadFiles),
                      trajectory.getFileName()
              ),
              WarningCode.LOAD_MISSING_TRAJECTORY_FOR_AREAS,
              studyId,
              userNni,
              trajectory
      );
    }
    return warningMessages;

  }

}
