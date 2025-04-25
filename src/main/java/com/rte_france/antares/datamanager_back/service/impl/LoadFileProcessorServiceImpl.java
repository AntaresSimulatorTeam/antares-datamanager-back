package com.rte_france.antares.datamanager_back.service.impl;

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
import java.nio.file.attribute.PosixFilePermissions;

import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class LoadFileProcessorServiceImpl implements LoadFileProcessorService {
  private final NasFileService nasFileService;
  private final TimeSeriesReader reader;
  private final TimeSeriesWriter writer;

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

}
