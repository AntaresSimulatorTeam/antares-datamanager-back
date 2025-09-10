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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;

class ThermalFileProcessorServiceImplTest {
    private static final String HORIZON_SHEET = "2025";
    private static final String THERMAL_PARAMETERS_FILE_NAME = "thermal_common_parameters_test.xlsx";
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

    private static byte[] generateCommonParametersExcelFile(String horizonSheetName) throws IOException {
        try (var outputStream = new ByteArrayOutputStream();
             var workbook = new XSSFWorkbook()) {
            workbook.createSheet(horizonSheetName);
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
    void processThermalCapacityFile_shouldThrowExceptionWhenTrajectorySaveFails() {
        Path mockPath = mock(Path.class);
        String horizon = "2025-2026";
        String area = "FR";
        List<ThermalClusterCapacityEntity> mockEntities = List.of(new ThermalClusterCapacityEntity());

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(trajectoryRepository.save(any())).thenThrow(new RuntimeException("Database error"));

        assertThrows(RuntimeException.class, () ->
                thermalFileProcessorService.processThermalCapacityFile(mockPath, horizon, mockEntities, TrajectoryType.THERMAL_CAPACITY, area, "CCGT")
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
        thermalFileProcessorService.processThermalCapacityFile(tempFile, horizon, thermalFileProcessorService.buildThermalClusterCapacityValuesList(tempFile, horizon, true,"FR","CCGD"), TrajectoryType.THERMAL_CAPACITY,"FR", "CCGD");

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


    @Test
    void findOrCreateThermalClusterRef_shouldCreateAndSaveNewClusterRef() {
        ThermalTechnology technology = ThermalTechnology.builder().name("CCGT").build();
        when(thermalTechnologyRepository.findThermalTechnologyByName("CCGT"))
                .thenReturn(Optional.of(technology));
        when(thermalClusterRefRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ThermalClusterRef result = thermalFileProcessorService.findOrCreateThermalClusterRef("CCGT", "Cluster2");

        assertNotNull(result);
        assertEquals("Cluster2", result.getName());
        assertEquals("CCGT", result.getThermalTechnology().getName());
        verify(thermalClusterRefRepository, times(1)).save(any());
    }

    @Test
    void findOrCreateThermalClusterRef_shouldCreateTechnologyWhenNotFound() {
        // Given
        String technology = "NewTech";
        String name = "ClusterA";
        when(thermalTechnologyRepository.findThermalTechnologyByName(technology)).thenReturn(Optional.empty());
        ThermalTechnology newTech = ThermalTechnology.builder().name(technology).build();
        when(thermalTechnologyRepository.save(any())).thenReturn(newTech);

        ThermalClusterRef expectedRef = ThermalClusterRef.builder()
                .name(name)
                .namePemmdb("NA")
                .thermalTechnology(newTech)
                .build();
        when(thermalClusterRefRepository.save(any())).thenReturn(expectedRef);

        // When
        ThermalClusterRef result = thermalFileProcessorService.findOrCreateThermalClusterRef(technology, name);

        // Then
        assertNotNull(result);
        assertEquals(technology, result.getThermalTechnology().getName());
        verify(thermalTechnologyRepository).save(any(ThermalTechnology.class));
    }

    @Test
    void saveThermalTrajectory_shouldThrowIllegalArgumentExceptionWhenEntityTypeIsInvalid() {
        TrajectoryEntity trajectory = new TrajectoryEntity();
        List<ThermalCommonParameterEntity> invalidEntities = List.of(new ThermalCommonParameterEntity());

        assertThrows(IllegalArgumentException.class, () ->
                thermalFileProcessorService.saveThermalTrajectory(trajectory, invalidEntities, TrajectoryType.THERMAL_CAPACITY));
    }
    @Test
    void buildThermalClusterCapacityValuesList_shouldThrowTechnicalExceptionWhenIOExceptionOccurs() throws IOException {
        Path mockPath = mock(Path.class);
        String horizon = "2025-2026";
        String area = "FR";
        String technology = "CCGT";

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.newInputStream(mockPath)).thenThrow(new IOException("File read error"));
            assertThrows(TechnicalException.class, () ->
                    thermalFileProcessorService.buildThermalClusterCapacityValuesList(mockPath, horizon, true, area, technology));
        }
    }
    @Test
    void isCellInHorizon_shouldReturnTrueWhenMonthIsInSecondHalfOfHorizonYear() {
        String monthYear = "2025_07";
        String horizon = "2025-2026";
        boolean isCivilYear = false;

        boolean result = thermalFileProcessorService.isCellInHorizon(monthYear, horizon, isCivilYear);

        assertTrue(result);
    }

    @Test
    void isCellInHorizon_shouldReturnTrueWhenMonthIsInFirstHalfOfNextYear() {
        String monthYear = "2026_03";
        String horizon = "2025-2026";
        boolean isCivilYear = false;

        boolean result = thermalFileProcessorService.isCellInHorizon(monthYear, horizon, isCivilYear);

        assertTrue(result);
    }

    @Test
    void isCellInHorizon_shouldReturnFalseWhenMonthIsBeforeJulyOfHorizonYear() {
        String monthYear = "2025_06";
        String horizon = "2025-2026";
        boolean isCivilYear = false;

        boolean result = thermalFileProcessorService.isCellInHorizon(monthYear, horizon, isCivilYear);

        assertFalse(result);
    }

    @Test
    void isCellInHorizon_shouldReturnFalseWhenMonthIsAfterJuneOfNextYear() {
        String monthYear = "2026_07";
        String horizon = "2025-2026";
        boolean isCivilYear = false;

        boolean result = thermalFileProcessorService.isCellInHorizon(monthYear, horizon, isCivilYear);

        assertFalse(result);
    }

    @Test
    void processThermalCommonParameterFile_shouldSaveTrajectoryAndLinkEntities(@TempDir Path tempDir) throws Exception {
        String horizon = "2025"; // sheet name expected by checksum computation
        Path file = mockExcelFile(tempDir, THERMAL_PARAMETERS_FILE_NAME, () -> generateCommonParametersExcelFile(horizon));

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());

        ThermalCommonParameterEntity e = new ThermalCommonParameterEntity();
        ArgumentCaptor<TrajectoryEntity> captor = ArgumentCaptor.forClass(TrajectoryEntity.class);
        when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = thermalFileProcessorService.processThermalCommonParameterFile(file, horizon, List.of(e), TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER);

        verify(trajectoryRepository).save(captor.capture());
        TrajectoryEntity saved = captor.getValue();
        assertEquals(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name(), saved.getType());
        assertSame(saved, e.getTrajectory(), "Entity should be linked to saved trajectory");
        assertEquals(result.getType(), saved.getType());
        assertEquals(horizon, saved.getHorizon());
        assertEquals(file.getFileName().toString().replaceFirst("\\.xlsx$", ""), saved.getFileName());
    }

    @Test
    void processThermalCommonParameterFile_shouldPropagateRepositoryException(@TempDir Path tempDir) throws Exception {
        String horizon = "2025";
        Path file = mockExcelFile(tempDir, THERMAL_PARAMETERS_FILE_NAME, () -> generateCommonParametersExcelFile(horizon));
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(trajectoryRepository.save(any())).thenThrow(new RuntimeException("DB down"));

        assertThrows(RuntimeException.class, () ->
                thermalFileProcessorService.processThermalCommonParameterFile(file, horizon, List.of(new ThermalCommonParameterEntity()), TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER)
        );
    }

    @Test
    void buildThermalCommonParameterValuesList_shouldParseRowsAfterHeader(@TempDir Path tempDir) throws Exception {
        // Create a workbook with a horizon sheet and one data row at row index 5 (rowNum > 4)
        Path file = mockExcelFile(tempDir, THERMAL_PARAMETERS_FILE_NAME, () -> {
            try (var contentStream = new ByteArrayOutputStream(); var wb = new XSSFWorkbook()) {
                var sheet = wb.createSheet(HORIZON_SHEET);
                // create the first 5 rows as headers/ignored
                for (int i = 0; i <= 4; i++) sheet.createRow(i);
                var row = sheet.createRow(5);

                row.createCell(0).setCellValue("pemmdb_name"); // clusterPemmdb
                row.createCell(1).setCellValue("ClusterA"); // clusterName
                row.createCell(2).setCellValue(1.0); // category (double)
                row.createCell(3).setCellValue("GAS"); // fuel
                row.createCell(4).setCellValue("CCGT"); // technology
                row.createCell(5).setCellValue("40-60"); // efficiencyRange
                row.createCell(6).setCellValue(0.55); // efficiencyDefault
                row.createCell(7).setCellValue(370.0); // co2
                row.createCell(8).setCellValue(2.3); // omCost
                row.createCell(9).setCellValue(4.0); // minUpTime
                row.createCell(10).setCellValue(3.0); // minDownTime
                row.createCell(11).setCellValue(10.0); // startUpFuel
                row.createCell(12).setCellValue(100.0); // startUpFixCost
                row.createCell(13).setCellValue(12.0); // startUpFuelColdStart
                row.createCell(14).setCellValue(120.0); // startUpFixCostColdStart
                row.createCell(15).setCellValue(8.0); // startUpFuelHotStart
                row.createCell(16).setCellValue(80.0); // startUpFixCostHotStart
                row.createCell(17).setCellValue(2.0); // transitionHotWarm
                row.createCell(18).setCellValue(5.0); // transitionHotCold
                row.createCell(19).setCellValue(1.0); // shutdownTime
                row.createCell(20).setCellValue(0.02); // foRateDefault
                row.createCell(21).setCellValue(10.0); // foDurationDefault
                row.createCell(22).setCellValue(3.0); // poDurationDefault
                row.createCell(23).setCellValue(1.0); // poWinterDefault
                row.createCell(24).setCellValue(15.0); // minStableGenerationDefault
                row.createCell(25).setCellValue(20.0); // rampUp
                row.createCell(26).setCellValue(21.0); // rampDown
                row.createCell(27).setCellValue(0.0); // fixedGenerationReduction
                wb.write(contentStream);
                return contentStream.toByteArray();
            }
        });

        // Mock repositories for findOrCreateThermalClusterRef
        when(thermalClusterRefRepository.findAll()).thenReturn(List.of());
        ThermalTechnology tech = ThermalTechnology.builder().name("CCGT").build();
        when(thermalTechnologyRepository.findThermalTechnologyByName("CCGT")).thenReturn(Optional.of(tech));
        when(thermalClusterRefRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var list = thermalFileProcessorService.buildThermalCommonParameterValuesList(file, HORIZON_SHEET, true);
        assertEquals(1, list.size());
        ThermalCommonParameterEntity e = list.get(0);
        assertNotNull(e.getThermalClusterRef());
        assertEquals("CCGT", e.getThermalClusterRef().getThermalTechnology().getName());
        assertEquals("ClusterA", e.getThermalClusterRef().getName());
        assertEquals(0.55, e.getEfficiencyDefault());
        assertEquals(370.0, e.getCo2());
    }

    @Test
    void buildThermalCommonParameterValuesList_shouldThrowTechnicalExceptionWhenHorizonSheetMissing(@TempDir Path tempDir) throws Exception {
        Path file = mockExcelFile(tempDir, THERMAL_PARAMETERS_FILE_NAME, () -> generateCommonParametersExcelFile("OTHER_SHEET"));
        TechnicalException ex = assertThrows(TechnicalException.class, () ->
                thermalFileProcessorService.buildThermalCommonParameterValuesList(file, HORIZON_SHEET, true)
        );
        assertTrue(ex.getMessage().contains("missing sheet"));
    }
}
