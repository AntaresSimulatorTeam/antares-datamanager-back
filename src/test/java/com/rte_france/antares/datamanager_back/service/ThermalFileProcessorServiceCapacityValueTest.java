package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalClusterRef;
import com.rte_france.antares.datamanager_back.repository.model.ThermalTechnology;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalClusterRefService;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalControlService;
import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalFileProcessorServiceImpl;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ThermalFileProcessorServiceCapacityValueTest {

    @InjectMocks
    private ThermalFileProcessorServiceImpl thermalFileProcessorService;

    @Mock
    private ThermalControlService thermalControlService;

    @Mock
    private AreaRepository areaRepository;

    @Mock
    private ThermalClusterRefService thermalClusterRefService;

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private StudyRepository studyRepository;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    private static byte[] generateCapacityExcelFileWithZeroNumber() throws IOException {
        try (var outputStream = new ByteArrayOutputStream();
             var workbook = new XSSFWorkbook()) {

            var sheet = workbook.createSheet("ThermalClusterCapacity");
            var headerRow = sheet.createRow(0);

            String[] baseHeaders = {"ToUse", "Area", "Technology", "Cluster", "Category"};
            String[] horizonHeaders = new String[12];
            for (int i = 0; i < 12; i++) {
                horizonHeaders[i] = String.format("2025_%02d", i + 1);
            }
            String[] headers = new String[baseHeaders.length + horizonHeaders.length];
            System.arraycopy(baseHeaders, 0, headers, 0, baseHeaders.length);
            System.arraycopy(horizonHeaders, 0, headers, baseHeaders.length, horizonHeaders.length);

            for (var i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            // Rows with category 'power' and 'number'
            Object[][] rowsData = {
                {1.0, "FR", "CCGT", "Cluster1", "power", 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0},
                {1.0, "FR", "CCGT", "Cluster1", "number", 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0}
            };

            for (var rowIndex = 0; rowIndex < rowsData.length; rowIndex++) {
                var row = sheet.createRow(rowIndex + 1);
                for (var colIndex = 0; colIndex < rowsData[rowIndex].length; colIndex++) {
                    if (rowsData[rowIndex][colIndex] instanceof Number) {
                        row.createCell(colIndex).setCellValue(((Number) rowsData[rowIndex][colIndex]).doubleValue());
                    } else {
                        row.createCell(colIndex).setCellValue(rowsData[rowIndex][colIndex].toString());
                    }
                }
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    @Test
    void buildThermalClusterCapacityValuesList_shouldThrowExceptionWhenNumberIsZero(@TempDir Path tempDir) throws Exception {
        Path tempFile = tempDir.resolve("thermal_capacity_zero.xlsx");
        Files.write(tempFile, generateCapacityExcelFileWithZeroNumber());

        when(areaRepository.findAllByStudyId(any())).thenReturn(List.of(AreaEntity.builder().id(1).name("FR").build()));
        when(thermalClusterRefService.findOrCreateThermalClusterRef(any(), any(), any()))
                .thenReturn(ThermalClusterRef.builder().name("Cluster1").thermalTechnology(ThermalTechnology.builder().name("CCGT").build()).build());

        String horizon = "2025-2026";

        BusinessException exception = assertThrows(BusinessException.class, () ->
                thermalFileProcessorService.buildThermalClusterCapacityValuesList(tempFile, horizon, true, "FR", "CCGT", 1)
        );

        assertTrue(exception.getMessage().contains("NUMBER values do not be <= 0 in THERMAL Installed Power trajectory"),
                "Expected error message not found. Actual: " + exception.getMessage());
        assertTrue(exception.getMessage().contains("thermal_capacity_zero.xlsx"));
    }
    @Test
    void buildThermalClusterCapacityValuesList_shouldThrowExceptionWhenNumberIsNegative(@TempDir Path tempDir) throws Exception {
        Path tempFile = tempDir.resolve("thermal_capacity_negative.xlsx");
        Files.write(tempFile, generateCapacityExcelFileWithNegativeNumber());

        when(areaRepository.findAllByStudyId(any())).thenReturn(List.of(AreaEntity.builder().id(1).name("FR").build()));
        when(thermalClusterRefService.findOrCreateThermalClusterRef(any(), any(), any()))
                .thenReturn(ThermalClusterRef.builder().name("Cluster1").thermalTechnology(ThermalTechnology.builder().name("CCGT").build()).build());

        String horizon = "2025-2026";

        BusinessException exception = assertThrows(BusinessException.class, () ->
                thermalFileProcessorService.buildThermalClusterCapacityValuesList(tempFile, horizon, true, "FR", "CCGT", 1)
        );

        assertTrue(exception.getMessage().contains("NUMBER values do not be <= 0 in THERMAL Installed Power trajectory"),
                "Expected error message not found. Actual: " + exception.getMessage());
        assertTrue(exception.getMessage().contains("thermal_capacity_negative.xlsx"));
    }

    private static byte[] generateCapacityExcelFileWithNegativeNumber() throws IOException {
        try (var outputStream = new ByteArrayOutputStream();
             var workbook = new XSSFWorkbook()) {

            var sheet = workbook.createSheet("ThermalClusterCapacity");
            var headerRow = sheet.createRow(0);

            String[] baseHeaders = {"ToUse", "Area", "Technology", "Cluster", "Category"};
            String[] horizonHeaders = new String[12];
            for (int i = 0; i < 12; i++) {
                horizonHeaders[i] = String.format("2025_%02d", i + 1);
            }
            String[] headers = new String[baseHeaders.length + horizonHeaders.length];
            System.arraycopy(baseHeaders, 0, headers, 0, baseHeaders.length);
            System.arraycopy(horizonHeaders, 0, headers, baseHeaders.length, horizonHeaders.length);

            for (var i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            // Rows with category 'power' and 'number' (negative value)
            Object[][] rowsData = {
                    {1.0, "FR", "CCGT", "Cluster1", "power", 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0},
                    {1.0, "FR", "CCGT", "Cluster1", "number", -1.0, -1.0, -1.0, -1.0, -1.0, -1.0, -1.0, -1.0, -1.0, -1.0, -1.0, -1.0}
            };

            for (var rowIndex = 0; rowIndex < rowsData.length; rowIndex++) {
                var row = sheet.createRow(rowIndex + 1);
                for (var colIndex = 0; colIndex < rowsData[rowIndex].length; colIndex++) {
                    if (rowsData[rowIndex][colIndex] instanceof Number) {
                        row.createCell(colIndex).setCellValue(((Number) rowsData[rowIndex][colIndex]).doubleValue());
                    } else {
                        row.createCell(colIndex).setCellValue(rowsData[rowIndex][colIndex].toString());
                    }
                }
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
