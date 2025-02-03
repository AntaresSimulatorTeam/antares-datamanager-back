package com.rte_france.antares.datamanager_back.util;

import org.apache.arrow.compression.CommonsCompressionFactory;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.compression.CompressionUtil;
import org.apache.arrow.vector.ipc.ArrowFileWriter;
import org.apache.arrow.vector.ipc.message.IpcOption;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ArrowWriter {
  private static final Logger LOGGER = LoggerFactory.getLogger(ArrowWriter.class);

  private static Field doubleField(String name) {
    return new Field(name, FieldType.notNullable(new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE)), null);
  }

  private static Schema createSchema(Matrix matrix) {
    var fields = matrix.getColumns().stream()
            .map(column -> doubleField(column.name()))
            .collect(Collectors.toList());
    return new Schema(fields);
  }

  private static void populateDoubleVector(VectorSchemaRoot table, MatrixColumn column) {
    var vector = table.getVector(column.name());
    var values = column.values();
    var size = values.length;
    switch (vector) {
      case Float8Vector f8Vector -> {
        f8Vector.allocateNew(size);
        table.setRowCount(size);
        IntStream.range(0, size).forEach(i -> f8Vector.set(i, values[i]));
      }
      default -> throw new IllegalStateException();
    }
  }

  public void write(Matrix matrix, OutputStream out) throws IOException {
    Objects.requireNonNull(matrix);
    Objects.requireNonNull(out);

    var schema = createSchema(matrix);
    try (var allocator = new RootAllocator();
         var table = VectorSchemaRoot.create(schema, allocator)) {
      matrix.getColumns().forEach(c -> populateDoubleVector(table, c));

      var compressionFactory = new CommonsCompressionFactory();
      try (var ch = Channels.newChannel(out);
           var writer = new ArrowFileWriter(table, null, ch, null, IpcOption.DEFAULT, compressionFactory, CompressionUtil.CodecType.ZSTD)) {
        writer.start();
        writer.writeBatch();
        writer.end();
      }
    }
  }

  public String getDefaultFileExtension() {
    return "arrow";
  }

  public static void main(String[] args) {
    var writer = new ArrowWriter();
    try {
      var matrix = ArrowReader.readMatrixFromTxt(Path.of("src/main/resources/INPUT/load/load_fr_2030-2031.txt"));

      var startSerialization = System.nanoTime();
      var arrowFilePath = Path.of("src/main/resources/test-matrix.arrow");
      try (var out = Files.newOutputStream(arrowFilePath)) {
        writer.write(matrix, out);
      }
      var endSerialization = System.nanoTime();
      var serializationTime = (endSerialization - startSerialization) / 1_000_000_000.0;
      var fileSize = Files.size(arrowFilePath);

      var startDeserialization = System.nanoTime();
      var deserializedMatrix = ArrowReader.readMatrixFromArrow(arrowFilePath);
      var endDeserialization = System.nanoTime();
      var deserializationTime = (endDeserialization - startDeserialization) / 1_000_000_000.0;

      LOGGER.info("Serialization time (s): {}", serializationTime);
      LOGGER.info("Deserialization time (s): {}", deserializationTime);
      LOGGER.info(".arrow file size (bytes): {}", fileSize);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}