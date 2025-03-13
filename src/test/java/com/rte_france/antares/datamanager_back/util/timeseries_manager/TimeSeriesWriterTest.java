package com.rte_france.antares.datamanager_back.util.timeseries_manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeSeriesWriterTest {
    private TimeSeriesWriter timeSeriesWriter;
    private TimeSeriesMatrix matrix;

    @BeforeEach
    void setUp() {
        timeSeriesWriter = new TimeSeriesWriter();
        var column = new TimeSeriesMatrixColumn("column1", new double[]{1.0, 2.0, 3.0});
        matrix = new TimeSeriesMatrix(List.of(column));
    }

    @Test
    void write_shouldWriteToFile(@TempDir Path tempDir) throws IOException {
        var outputPath = tempDir.resolve("output.arrow");
        timeSeriesWriter.write(matrix, outputPath);

        assertTrue(Files.exists(outputPath));
        assertTrue(Files.size(outputPath) > 0);
    }

    @Test
    void writeToByteArray_shouldReturnByteArray() throws IOException {
        var result = timeSeriesWriter.writeToByteArray(matrix);

        assertTrue(result.length > 0);
    }

    @Test
    void writeAndWriteToByteArray_shouldProduceSameOutput(@TempDir Path tempDir) throws IOException {
        var outputPath = tempDir.resolve("output.arrow");
        timeSeriesWriter.write(matrix, outputPath);
        var fileBytes = Files.readAllBytes(outputPath);
        var byteArray = timeSeriesWriter.writeToByteArray(matrix);

        assertArrayEquals(fileBytes, byteArray);
    }
}