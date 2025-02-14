package com.rte_france.antares.timeseries_manager.main;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

public class Main {
  private static final Logger LOGGER = Logger.getLogger(Main.class.getSimpleName());
  private static final String inputPath = "src/main/resources/INPUT/load/load_fr_2030-2031.txt";
  private static final String outputPath = "src/main/resources/INPUT/output.";

  public static void main(String[] args) {
    var writer = new ArrowTSWriter();
    var reader = new ArrowTSReader();

    try {
      var matrix = reader.readFromTxt(Path.of(inputPath));

      var startSerialization = System.nanoTime();
      var parquetFilePath = Path.of(outputPath + writer.getDefaultFileExtension());
      writer.write(matrix, parquetFilePath);
      var endSerialization = System.nanoTime();
      var serializationTime = (endSerialization - startSerialization) / 1_000_000_000.0;
      var fileSize = Files.size(parquetFilePath);

      var startDeserialization = System.nanoTime();
      var deserializedMatrix = reader.read(parquetFilePath);
      var endDeserialization = System.nanoTime();
      var deserializationTime = (endDeserialization - startDeserialization) / 1_000_000_000.0;

      LOGGER.info("Serialization time: " + serializationTime);
      LOGGER.info("Deserialization time: " +deserializationTime);
      LOGGER.info(".parquet file size (bytes): " + fileSize);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
