package com.rte_france.antares.datamanager_back.service;


import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.ThermalClusterRefRepository;
import com.rte_france.antares.datamanager_back.repository.ThermalTechnologyRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.impl.ThermalFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.impl.UserService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Disabled
class ThermalFileProcessorServiceImplTest {
    private static final String THERMAL_CAPACITY_FILE_NAME = "thermal_BE_PEMMDB23_26avril";

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private UserService userService;

    @Mock
    ThermalClusterRefRepository thermalClusterRefRepository;

    @Mock
    ThermalTechnologyRepository thermalTechnologyRepository;

    @InjectMocks
    private ThermalFileProcessorServiceImpl thermalFileProcessorService;

    @BeforeEach
     void setup() {
        MockitoAnnotations.openMocks(this);
    }

    private static byte[] generateCapacityExcelFile() throws IOException {
        try (var outputStream = new ByteArrayOutputStream();
             var workbook = new XSSFWorkbook()) {

            var sheet = workbook.createSheet("ThermalClusterCapacity");
            var headerRow = sheet.createRow(0);

            String[] headers = {"ToUse", "Area", "Type", "Cluster", "Category", "2025_01", "2025_02", "2025_03"};
            for (var i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            Object[][] data = {
                    {0.0, "FR", "CCGT", "Cluster1", "power", 100.0, 120.0, 130.0},
                    {1.0, "AT", "CCGT", "Cluster2", "number", 90.0, 110.0, 125.0}
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
    void processThermalCapacityFile_shouldSaveTrajectoryWithValidInputs() throws IOException {
        Path mockPath = mock(Path.class);
        String horizon = "2025-2026";
        String area = "FR";
        List<ThermalClusterCapacityEntity> mockEntities = List.of(new ThermalClusterCapacityEntity());
        TrajectoryEntity mockTrajectory = new TrajectoryEntity();

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(trajectoryRepository.save(any())).thenReturn(mockTrajectory);

        TrajectoryEntity result = thermalFileProcessorService.processThermalCapacityFile(mockPath, horizon, mockEntities, TrajectoryType.THERMAL_CAPACITY, area);

        assertNotNull(result);
        assertEquals(mockTrajectory, result);
        verify(trajectoryRepository, times(1)).save(any());
    }

    @Test
    void processThermalCapacityFile_shouldThrowExceptionWhenUserDetailsAreNull() {
        Path mockPath = mock(Path.class);
        String horizon = "2025-2026";
        String area = "FR";
        List<ThermalClusterCapacityEntity> mockEntities = List.of(new ThermalClusterCapacityEntity());

        when(userService.getCurrentUserDetails()).thenReturn(null);

        assertThrows(TechnicalException.class, () ->
                thermalFileProcessorService.processThermalCapacityFile(mockPath, horizon, mockEntities, TrajectoryType.THERMAL_CAPACITY, area)
        );
    }

    @Test
    void processThermalCapacityFile_shouldThrowExceptionWhenTrajectorySaveFails() {
        Path mockPath = mock(Path.class);
        String horizon = "2025-2026";
        String area = "FR";
        List<ThermalClusterCapacityEntity> mockEntities = List.of(new ThermalClusterCapacityEntity());

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(trajectoryRepository.save(any())).thenThrow(new RuntimeException("Database error"));

        assertThrows(RuntimeException.class, () ->
                thermalFileProcessorService.processThermalCapacityFile(mockPath, horizon, mockEntities, TrajectoryType.THERMAL_CAPACITY, area)
        );
    }

    @Test
    void processThermalCapacityFile_whenTrajectoryExistsAndVersionIsValid(@TempDir Path tempDir) throws Exception {
        var tempFile = mockExcelFile(tempDir, THERMAL_CAPACITY_FILE_NAME, ThermalFileProcessorServiceImplTest::generateCapacityExcelFile);
        TrajectoryEntity trajectoryEntity = mock(TrajectoryEntity.class);
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(any(),any(), any())).thenReturn(Optional.of(trajectoryEntity));
        when(thermalTechnologyRepository.findThermalTechnologyByName(any())).thenReturn(Optional.of(ThermalTechnology.builder().name("CCGT").build()));
        when(thermalClusterRefRepository.findAll()).thenReturn(List.of(ThermalClusterRef.builder().name("Cluster1").thermalTechnology(ThermalTechnology.builder().name("CCGT").build()).build()));
        when(trajectoryRepository.save(any())).thenReturn(trajectoryEntity);
        var horizon = "2025-2026";
        thermalFileProcessorService.processThermalCapacityFile(tempFile, horizon, thermalFileProcessorService.buildThermalClusterCapacityValuesList(tempFile, horizon, true,"FR","CCGT"), TrajectoryType.THERMAL_CAPACITY,"FR");

        verify(trajectoryRepository, times(1)).save(any());
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
}
