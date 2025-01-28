package com.rte_france.antares.datamanager_back.util;

import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetFileWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.LocalOutputFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public class TimeSeriesWriter {
  public static TimeSeriesMatrix readFromTxt(Path filePath) throws IOException {
    Objects.requireNonNull(filePath);
    try (var lines = Files.lines(filePath)) {
      var rows = lines.map(line -> {
        var values = line.trim().split("\\s+");
        var doubles = Arrays.stream(values)
                .map(Double::parseDouble)
                .toList();
        return new TimeSeriesRow(doubles);
      }).collect(Collectors.toList());
      return new TimeSeriesMatrix(rows);
    }
  }

  public static void writeToParquet(TimeSeriesMatrix matrix, Path outputPath) throws IOException {
    Objects.requireNonNull(matrix);
    Objects.requireNonNull(outputPath);
    if (!(outputPath + "").endsWith(".parquet")) {
      outputPath = outputPath.resolveSibling(outputPath.getFileName() + ".parquet");
    }

    var outputFile = new LocalOutputFile(outputPath);
    try (var writer = AvroParquetWriter
            .<TimeSeriesMatrix>builder(outputFile)
            .withSchema(TimeSeriesMatrix.getClassSchema())
            .withCompressionCodec(CompressionCodecName.ZSTD)
            .withByteStreamSplitEncoding(true)
            .withRowGroupSize((long) ParquetWriter.DEFAULT_BLOCK_SIZE)
            .withPageSize(ParquetWriter.DEFAULT_PAGE_SIZE)
            .withWriteMode(ParquetFileWriter.Mode.OVERWRITE)
            .build()) {

      writer.write(matrix);
    }
  }

  public static void main(String[] args) {
    try {
      var matrix = TimeSeriesWriter.readFromTxt(Path.of("src/main/resources/INPUT/load/load_fr_2030-2031.txt"));

      var startSerialization = System.nanoTime();
      var parquetFilePath = Path.of("src/main/resources/INPUT/load/output_test.parquet");
      TimeSeriesWriter.writeToParquet(matrix, parquetFilePath);
      var endSerialization = System.nanoTime();
      var serializationTime = (endSerialization - startSerialization) / 1_000_000_000.0;
      var fileSize = Files.size(parquetFilePath);

      var startDeserialization = System.nanoTime();
      var deserializedMatrix = TimeSeriesReader.readFromParquet(parquetFilePath);
      var endDeserialization = System.nanoTime();
      var deserializationTime = (endDeserialization - startDeserialization) / 1_000_000_000.0;

      System.out.println("Serialization time: " + serializationTime);
      System.out.println("Deserialization time: " + deserializationTime);
      System.out.println(".parquet file size (bytes): " + fileSize);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}