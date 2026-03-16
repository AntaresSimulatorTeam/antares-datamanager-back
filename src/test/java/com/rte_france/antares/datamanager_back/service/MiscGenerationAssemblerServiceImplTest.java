package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.misc.impl.MiscGenerationAssemblerServiceImpl;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrixColumn;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrix;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class MiscGenerationAssemblerServiceImplTest {

    @Mock
    private NasFileService nasFileService;

    @Mock
    private AntaresDataManagerProperties antaresDataManagerProperties;

    @Mock
    private TimeSeriesReader timeSeriesReader;

    @InjectMocks
    private MiscGenerationAssemblerServiceImpl miscGenerationAssemblerService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        miscGenerationAssemblerService = new MiscGenerationAssemblerServiceImpl(
                null, // areaRepository
                null, // miscFileProcessorService
                nasFileService,
                antaresDataManagerProperties,
                timeSeriesReader
        );
    }

    @Test
    void splitMiscGenLoadFiles_shouldIncludeGroupNameInFileName() throws IOException {
        // Given
        Path tempFile = tempDir.resolve("load_factor_wave_2030-2031.csv");
        Files.createFile(tempFile);
        Set<String> areas = Set.of("AT", "BE");
        String horizon = "2030-2031";
        String groupName = "wave";
        String outputDir = "misc_gen_ts";

        TimeSeriesMatrixColumn colAT = new TimeSeriesMatrixColumn("AT", new double[]{0.1, 0.2});
        TimeSeriesMatrixColumn colBE = new TimeSeriesMatrixColumn("BE", new double[]{0.3, 0.4});
        TimeSeriesMatrix matrix = new TimeSeriesMatrix(List.of(colAT, colBE));

        when(timeSeriesReader.readFromTxt(tempFile)).thenReturn(matrix);
        when(antaresDataManagerProperties.getMiscGenTsOutputDirectory()).thenReturn(outputDir);
        when(nasFileService.saveMatrixToNas(any(TimeSeriesMatrix.class), eq("AT_wave"), eq(outputDir))).thenReturn("AT_wave.UUID.arrow");
        when(nasFileService.saveMatrixToNas(any(TimeSeriesMatrix.class), eq("BE_wave"), eq(outputDir))).thenReturn("BE_wave.UUID.arrow");

        // When
        List<Path> results = miscGenerationAssemblerService.splitMiscGenLoadFiles(tempFile, areas, horizon, groupName);

        // Then
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(p -> p.toString().contains("AT_wave.UUID.arrow")));
        assertTrue(results.stream().anyMatch(p -> p.toString().contains("BE_wave.UUID.arrow")));
    }

    @Test
    void splitMiscGenLoadFiles_shouldNotIncludeGroupNameIfEmpty() throws IOException {
        // Given
        Path tempFile = tempDir.resolve("load_factor_2030-2031.csv");
        Files.createFile(tempFile);
        Set<String> areas = Set.of("AT");
        String horizon = "2030-2031";
        String groupName = "";
        String outputDir = "misc_gen_ts";

        TimeSeriesMatrixColumn colAT = new TimeSeriesMatrixColumn("AT", new double[]{0.1, 0.2});
        TimeSeriesMatrix matrix = new TimeSeriesMatrix(List.of(colAT));

        when(timeSeriesReader.readFromTxt(tempFile)).thenReturn(matrix);
        when(antaresDataManagerProperties.getMiscGenTsOutputDirectory()).thenReturn(outputDir);
        when(nasFileService.saveMatrixToNas(any(TimeSeriesMatrix.class), eq("AT"), eq(outputDir))).thenReturn("AT.UUID.arrow");

        // When
        List<Path> results = miscGenerationAssemblerService.splitMiscGenLoadFiles(tempFile, areas, horizon, groupName);

        // Then
        assertEquals(1, results.size());
        assertTrue(results.get(0).toString().contains("AT.UUID.arrow"));
    }
}
