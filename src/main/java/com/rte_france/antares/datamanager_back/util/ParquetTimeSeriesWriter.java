/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.rte_france.antares.datamanager_back.util;

import com.google.common.base.Stopwatch;
import org.apache.parquet.column.ParquetProperties;
import org.apache.parquet.hadoop.ParquetFileWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Objects;

/**
 *
 * Uses the standard "row based" API provided by "parquet-mr" implementation.
 * Tests show that the implementation does not scale well with number of columns.
 * Indeed it's weird to have a row-based implementation when the format is column-based ...
 * Spark seems to have implemented a "vectorized" reader, but not a writer.
 *
 * @author Sylvain Leclerc <sylvain.leclerc@rte-france.com>
 */
public class ParquetTimeSeriesWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParquetTimeSeriesWriter.class);

    private final CompressionCodecName compression;

    public ParquetTimeSeriesWriter() {
        this(CompressionCodecName.SNAPPY);
    }

    public ParquetTimeSeriesWriter(CompressionCodecName compression) {
        this.compression = Objects.requireNonNull(compression);
    }

    public void write(Matrix matrix, OutputStream outputStream) throws IOException {
        throw new UnsupportedOperationException("Writing to stream not supported for parquet format.");
    }

    public void write(Matrix matrix, Path file) throws IOException {
        var writer = new ParquetWriterBuilder(matrix, file)
                .withWriteMode(ParquetFileWriter.Mode.OVERWRITE)
                .withCompressionCodec(compression)
                .withDictionaryEncoding(false)
                .withBloomFilterEnabled(false)
                .withByteStreamSplitEncoding(false)
                .withWriterVersion(ParquetProperties.WriterVersion.PARQUET_2_0)
                .withPageWriteChecksumEnabled(false)
                .withRowGroupSize((long) 1024*1024*128) // Default 1024*1024*128
                .withPageRowCountLimit(20000) // Default 20 000
                .withPageSize(1024*1024) //Default 1024 * 1024
                .build();
        Stopwatch timer = Stopwatch.createStarted();
        for (var r = 0; r < matrix.getRowCount(); r++) {
            var row = new MatrixRow(matrix, r);
            writer.write(row);
        }
        LOGGER.info("All rows written in {}", timer);
        writer.close();
        LOGGER.info("Writer closed after {}", timer);
    }

    public String getDefaultFileExtension() {
        return "parquet";
    }

}
