package com.rte_france.antares.datamanager_back.util;

import jakarta.validation.constraints.Negative;
import org.apache.arrow.compression.CommonsCompressionFactory;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.compression.CompressionCodec;
import org.apache.arrow.compression.ZstdCompressionCodec;
import org.apache.arrow.vector.compression.CompressionUtil;
import org.apache.arrow.vector.ipc.ArrowFileWriter;
import org.apache.arrow.vector.ipc.message.IpcOption;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ArrowWriter {

  private static final BufferAllocator ALLOCATOR = new RootAllocator();

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
    var vector = (Float8Vector) table.getVector(column.name());
    var values = column.values();
    var size = values.length;
    vector.allocateNew(size);
    table.setRowCount(size);
    IntStream.range(0, size).forEach(i -> vector.set(i, values[i]));
  }

  public void write(Matrix matrix, OutputStream out) throws IOException {
    var schema = createSchema(matrix);
    try (var table = VectorSchemaRoot.create(schema, ALLOCATOR)) {
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
      var matrix = ArrowReader.readMatrixFromTxt(Path.of("src/main/resources/INPUT/load/series.txt"));

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

      System.out.println("Serialization time (s): " + serializationTime);
      System.out.println("Deserialization time (s): " + deserializationTime);
      System.out.println(".arrow file size (bytes): " + fileSize);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}