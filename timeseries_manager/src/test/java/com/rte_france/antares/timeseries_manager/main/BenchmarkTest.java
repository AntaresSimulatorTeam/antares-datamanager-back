package com.rte_france.antares.timeseries_manager.main;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BenchmarkTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(BenchmarkTest.class);

  @Test
  public void testSerializationAndDeserialization() {
    var writer = new ArrowTSWriter();
    var reader = new ArrowTSReader();
    try {
      var testFile = "load_fr_2030-2031.txt";
      var matrix = reader.readFromTxt(Path.of("src/test/resources/INPUT/load/" + testFile));
      assertNotNull(matrix, "Matrix should not be null");

      var startSerialization = System.nanoTime();
      var filePath = Path.of("src/test/resources/OUTPUT/output_matrix." + writer.getDefaultFileExtension());
      writer.write(matrix, filePath);
      var endSerialization = System.nanoTime();
      var serializationTime = (endSerialization - startSerialization) / 1_000_000_000.0;
      var fileSize = Files.size(filePath);

      var startDeserialization = System.nanoTime();
      var deserializedMatrix = reader.read(filePath);
      var endDeserialization = System.nanoTime();
      var deserializationTime = (endDeserialization - startDeserialization) / 1_000_000_000.0;

      assertNotNull(deserializedMatrix, "Matrix shouldn't be null");
      assertTrue(fileSize > 0);

      LOGGER.info("Serialization time (s): {}", serializationTime);
      LOGGER.info("Deserialization time (s): {}", deserializationTime);
      LOGGER.info(".arrow file size (bytes): {}", fileSize);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}