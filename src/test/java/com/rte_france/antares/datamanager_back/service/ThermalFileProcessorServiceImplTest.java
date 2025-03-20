package com.rte_france.antares.datamanager_back.service;


import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.repository.ThermalCostTypeRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.impl.ThermalFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.impl.UserService;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ThermalFileProcessorServiceImplTest {
    private static final String THERMAL_CAPACITY_FILE_NAME = "thermal_BE_PEMMDB23_26avril";
    private static final String THERMAL_PARAMETERS_FILE_NAME = "common_param_BP23_A_ref";
    private static final String THERMAL_PARAMETERS_PATH = "src/test/resources/thermal_parameters/" + THERMAL_PARAMETERS_FILE_NAME + ".xlsx";
    private static final String THERMAL_COSTS_FILE_NAME = "costs_BP23_A_ref";

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private ThermalCostTypeRepository thermalCostTypeRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private ThermalFileProcessorServiceImpl thermalFileProcessorService;

    @BeforeEach
    public void setup(@TempDir Path tempDir) throws IOException {
        MockitoAnnotations.openMocks(this);
    }

    private static byte[] generateCapacityExcelFile() throws IOException {
        try (var outputStream = new ByteArrayOutputStream();
             var workbook = new XSSFWorkbook()) {

            var sheet = workbook.createSheet("ThermalClusterCapacity");
            var headerRow = sheet.createRow(0);

            String[] headers = {"ToUse", "Scenario", "DefaultScenario", "Name", "Category", "Jan-2025", "Feb-2025", "Mar-2025"};
            for (var i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            Object[][] data = {
                    {0.0, "ScenarioA", 1.0, "Cluster1", "power", 100.0, 120.0, 130.0},
                    {1.0, "ScenarioB", 0.0, "Cluster2", "number", 90.0, 110.0, 125.0}
            };

            for (var rowIndex = 0; rowIndex < data.length; rowIndex++) {
                var row = sheet.createRow(rowIndex + 1);
                for (var colIndex = 0; colIndex < data[rowIndex].length; colIndex++) {
                    if (data[rowIndex][colIndex] instanceof Number) {
                        row.createCell(colIndex).setCellValue(((Number) data[rowIndex][colIndex]).doubleValue());
                    } else {
                        row.createCell(colIndex).setCellValue(data[rowIndex][colIndex].toString());
                    }
                }
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private static byte[] generateCostExcelFile() throws IOException {
        try (var outputStream = new ByteArrayOutputStream();
             var workbook = new XSSFWorkbook()) {

            var sheet = workbook.createSheet("ThermalCosts");
            var headerRow = sheet.createRow(0);

            String[] headers = {"country", "fuel", "scenario", "comment", "unit", "modulation", "ratio_NCV_HCV"};
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            int yearStartIndex = headers.length;
            for (int i = 0; i < 3; i++) { // Example years: 2025, 2026, 2027
                headerRow.createCell(yearStartIndex + i).setCellValue(2025 + i);
            }
            Object[][] data = {
                    {"CountryA", "Coal", "Scenario1", "Comment1", "kWh", "High", 0.85, 100.0, 120.0, 130.0, 140.0, 150.0},
                    {"CountryB", "Gas", "Scenario2", "Comment2", "MWh", "Low", 0.9, 110.0, 125.0, 135.0, 145.0, 155.0}
            };

            for (var rowIndex = 0; rowIndex < data.length; rowIndex++) {
                var row = sheet.createRow(rowIndex + 1); // Data rows start from row 1
                for (var colIndex = 0; colIndex < data[rowIndex].length; colIndex++) {
                    if (data[rowIndex][colIndex] instanceof Number) {
                        row.createCell(colIndex).setCellValue(((Number) data[rowIndex][colIndex]).doubleValue());
                    } else {
                        row.createCell(colIndex).setCellValue(data[rowIndex][colIndex].toString());
                    }
                }
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private static Path mockExcelFile(Path tempDir, String fileName, ByteSupplier excelFileSupplier) throws IOException {
        var tempFile = tempDir.resolve(fileName);
        try (var outputStream = Files.newOutputStream(tempFile)) {
            outputStream.write(excelFileSupplier.get());
        }
        return tempFile;
    }

    @FunctionalInterface
    interface ByteSupplier {
        byte[] get() throws IOException;
    }

    @Test
    void processThermalCapacityFile_whenTrajectoryExistsAndVersionIsValid(@TempDir Path tempDir) throws IOException {
        var tempFile = mockExcelFile(tempDir, THERMAL_CAPACITY_FILE_NAME, ThermalFileProcessorServiceImplTest::generateCapacityExcelFile);
        TrajectoryEntity trajectoryEntity = mock(TrajectoryEntity.class);
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.of(trajectoryEntity));
        var horizon = "2023-2024";
        thermalFileProcessorService.processThermalFile(tempFile, horizon, thermalFileProcessorService::buildThermalClusterCapacityValuesList, TrajectoryType.THERMAL_CAPACITY);

        verify(trajectoryRepository, times(1)).save(any());
    }

    @Test
    void processThermalCapacityFile_whenTrajectoryDoesNotExist(@TempDir Path tempDir) throws IOException {
        var tempFile = mockExcelFile(tempDir, THERMAL_CAPACITY_FILE_NAME, ThermalFileProcessorServiceImplTest::generateCapacityExcelFile);
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.empty());
        var horizon = "2023-2024";
        thermalFileProcessorService.processThermalFile(tempFile, horizon, thermalFileProcessorService::buildThermalClusterCapacityValuesList, TrajectoryType.THERMAL_PARAMETER);

        verify(trajectoryRepository, times(1)).save(any());
    }

    @Test
    void processThermalParameterFile() throws IOException {
        // Given

        Path path = Path.of(THERMAL_PARAMETERS_PATH);

        String horizon = "2025";

        TrajectoryEntity expectedTrajectory = TrajectoryEntity.builder()
                .fileName(THERMAL_PARAMETERS_FILE_NAME)
                .type(TrajectoryType.THERMAL_PARAMETER.name())
                .version(1)
                .horizon(horizon)
                .build();

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(THERMAL_PARAMETERS_FILE_NAME + ".xlsx"))
                .thenReturn(Optional.of(expectedTrajectory));
        when(trajectoryRepository.save(any())).thenReturn(expectedTrajectory);

        // When
        thermalFileProcessorService.processThermalFile(path, horizon, thermalFileProcessorService::buildThermalParameters, TrajectoryType.THERMAL_PARAMETER);

        // Then
        verify(trajectoryRepository, times(1))
                .findFirstByFileNameOrderByVersionDesc(THERMAL_PARAMETERS_FILE_NAME);

        verify(trajectoryRepository, times(1)).save(any(TrajectoryEntity.class));

    }

    @Test
    void processThermalCostFile_whenTrajectoryExistsAndVersionIsValid(@TempDir Path tempDir) throws IOException {
        // Given
        var tempFile = mockExcelFile(tempDir, THERMAL_COSTS_FILE_NAME, ThermalFileProcessorServiceImplTest::generateCostExcelFile);
        TrajectoryEntity trajectoryEntity = mock(TrajectoryEntity.class);
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.of(trajectoryEntity));
        String horizon = "2025";

        // When
        thermalFileProcessorService.processThermalFile(tempFile, horizon, thermalFileProcessorService::buildThermalCosts, TrajectoryType.THERMAL_COST);

        // Then
        verify(trajectoryRepository, times(1)).save(any(TrajectoryEntity.class));
    }

    @Test
    void processThermalCostFile_whenTrajectoryDoesNotExist(@TempDir Path tempDir) throws IOException {
        // Given
        var tempFile = mockExcelFile(tempDir, THERMAL_COSTS_FILE_NAME, ThermalFileProcessorServiceImplTest::generateCostExcelFile);
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.empty());
        String horizon = "2025";

        // When
        thermalFileProcessorService.processThermalFile(tempFile, horizon, thermalFileProcessorService::buildThermalParameters, TrajectoryType.THERMAL_PARAMETER);

        // Then
        verify(trajectoryRepository, times(1)).save(any(TrajectoryEntity.class));
    }

    @Test
    void buildThermalParameters() throws IOException {
        // Given
        Path path = Path.of(THERMAL_PARAMETERS_PATH);

        // When
        List<ThermalParameterEntity> thermalParameters = thermalFileProcessorService.buildThermalParameters(path);

        // Then
        assertEquals(47, thermalParameters.size());
    }

    @Test
    void saveThermalCapacitiesTrajectory() {
        // Given
        TrajectoryEntity trajectory = new TrajectoryEntity();
        List<ThermalClusterCapacityEntity> thermalClusterCapacities = List.of(new ThermalClusterCapacityEntity());

        when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenReturn(trajectory);

        // When
        TrajectoryEntity result = thermalFileProcessorService.saveThermalTrajectory(trajectory, thermalClusterCapacities, TrajectoryType.THERMAL_CAPACITY);

        // Then
        assertEquals(TrajectoryType.THERMAL_CAPACITY.name(), result.getType());
        verify(trajectoryRepository).save(any(TrajectoryEntity.class));
    }

    @Test
    void saveThermalParametersTrajectory() {
        // Given
        TrajectoryEntity trajectory = new TrajectoryEntity();
        List<ThermalParameterEntity> thermalParameterEntities = List.of(new ThermalParameterEntity());

        when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenReturn(trajectory);

        // When
        TrajectoryEntity result = thermalFileProcessorService.saveThermalTrajectory(trajectory, thermalParameterEntities, TrajectoryType.THERMAL_PARAMETER);

        // Then
        assertEquals(TrajectoryType.THERMAL_PARAMETER.name(), result.getType());
        verify(trajectoryRepository).save(any(TrajectoryEntity.class));
    }

    @Test
    void saveThermalCostTrajectory() {
        // Given
        TrajectoryEntity trajectory = new TrajectoryEntity();
        List<ThermalCostEntity> thermalCostEntities = List.of(new ThermalCostEntity(10.0, 2036.0, new ThermalCostTypeEntity()));

        when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenReturn(trajectory);

        // When
        TrajectoryEntity result = thermalFileProcessorService.saveThermalTrajectory(trajectory, thermalCostEntities, TrajectoryType.THERMAL_COST);

        // Then
        assertEquals(TrajectoryType.THERMAL_COST.name(), result.getType());
        verify(trajectoryRepository).save(any(TrajectoryEntity.class));
    }
}
