/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.rte_france.antares.datamanager_back.util;

import com.google.common.base.Stopwatch;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.column.ParquetProperties;
import org.apache.parquet.hadoop.ParquetFileWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class ParquetTimeSeriesWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParquetTimeSeriesWriter.class);

    private final CompressionCodecName compression;

    public ParquetTimeSeriesWriter() {
        this(CompressionCodecName.ZSTD);
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
                .withRowGroupSize((long) ParquetWriter.DEFAULT_BLOCK_SIZE) // Default 1024*1024*128
                .withPageRowCountLimit(20_000) // Default 20 000
                .withPageSize(ParquetWriter.DEFAULT_PAGE_SIZE) //Default 1024 * 1024
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

    public static void main(String[] args) {
        try {
            var matrix = ParquetTimeSeriesReader.readMatrixFromTxt(Path.of("src/main/resources/INPUT/load/load_fr_2030-2031.txt"));
            var writer = new ParquetTimeSeriesWriter();
            var startSerialization = System.nanoTime();
            var parquetFilePath = Path.of("src/main/resources/INPUT/load/output_test.parquet");
            var hadoopFilePath = new org.apache.hadoop.fs.Path(parquetFilePath.toUri());
            writer.write(matrix, parquetFilePath);
            var endSerialization = System.nanoTime();
            var serializationTime = (endSerialization - startSerialization) / 1_000_000_000.0;
            var fileSize = Files.size(parquetFilePath);

            var startDeserialization = System.nanoTime();
            var deserializedMatrix = ParquetTimeSeriesReader.readFromParquet(hadoopFilePath);
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
