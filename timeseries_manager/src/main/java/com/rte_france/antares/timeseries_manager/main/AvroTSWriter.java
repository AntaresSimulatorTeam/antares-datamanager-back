/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.rte_france.antares.timeseries_manager.main;

import com.rte_france.antares.timeseries_manager.util.Utils;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetFileWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.LocalOutputFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

public final class AvroTSWriter implements TimeSeriesWriter<TimeSeriesMatrix> {
  @Override
  public void write(TimeSeriesMatrix matrix, Path outputPath) throws IOException {
    Objects.requireNonNull(matrix);
    Objects.requireNonNull(outputPath);
    outputPath = Utils.ensureExtension(outputPath, this::getDefaultFileExtension);

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

  @Override
  public String getDefaultFileExtension() {
    return "parquet";
  }
}