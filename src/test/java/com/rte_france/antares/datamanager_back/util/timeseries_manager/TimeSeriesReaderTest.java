package com.rte_france.antares.datamanager_back.util.timeseries_manager;

import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class TimeSeriesReaderTest {

    private TimeSeriesReader timeSeriesReader;

    @BeforeEach
    void setUp() {
        timeSeriesReader = new TimeSeriesReader();
    }

    @Test
    void readFromTxt_shouldReadCorrectly(@TempDir Path tempDir) throws IOException {
        var filePath = tempDir.resolve("timeseries.txt");
        Files.createFile(filePath);
        Files.writeString(filePath, "1.0 2.0 3.0\n4.0 5.0 6.0\n7.0 8.0 9.0");
        var matrix = timeSeriesReader.readFromTxt(filePath);

        assertEquals(8760, matrix.getRowCount());
        assertEquals(3, matrix.columns().size());
        assertArrayEquals(new double[]{3.0, 6.0, 9.0}, Arrays.copyOf(matrix.columns().get(2).values(), 3));
    }

    @Test
    void readFromTxt_shouldThrowExceptionForEmptyFile(@TempDir Path tempDir) throws IOException {
        var filePath = tempDir.resolve("empty.txt");
        Files.createFile(filePath);

        var exception = assertThrows(TechnicalException.class,
                () -> timeSeriesReader.readFromTxt(filePath));
        assertEquals("File is empty", exception.getMessage());
    }
}