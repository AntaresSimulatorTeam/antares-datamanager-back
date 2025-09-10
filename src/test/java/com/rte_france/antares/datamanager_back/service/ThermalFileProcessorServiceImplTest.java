package com.rte_france.antares.datamanager_back.service;


import com.rte_france.antares.datamanager_back.dto.ThermalClusterCapacityDto;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
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
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

    @Mock
    private AreaRepository areaRepository;

    @BeforeEach
     void setup() {
        MockitoAnnotations.openMocks(this);
    }

    private static byte[] generateCapacityExcelFile() throws IOException {
        try (var outputStream = new ByteArrayOutputStream();
             var workbook = new XSSFWorkbook()) {

            var sheet = workbook.createSheet("ThermalClusterCapacity");
            var headerRow = sheet.createRow(0);

            // Colonnes de l’année civile 2025 (janvier à décembre)
            String[] baseHeaders = {"ToUse", "Area", "Type", "Cluster", "Category"};
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

            // Remplir les données avec des valeurs fictives pour chaque colonne
            Object[][] data = {
                    {0.0, "FR", "CCGT", "Cluster1", "power", 100.0, 120.0, 130.0, 140.0, 150.0, 160.0, 170.0, 180.0, 190.0, 200.0, 210.0, 220.0, 230.0},
                    {0.0, "FR", "CCGT", "Cluster1", "number", 100.0, 120.0, 130.0, 140.0, 150.0, 160.0, 170.0, 180.0, 190.0, 200.0, 210.0, 220.0, 230.0},
                    {1.0, "AT", "CCGT", "Cluster2", "number", 90.0, 110.0, 125.0, 135.0, 145.0, 155.0, 165.0, 175.0, 185.0, 195.0, 205.0, 215.0, 225.0}
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
    void processThermalCapacityFile_shouldThrowExceptionWhenTrajectorySaveFails() {
        Path mockPath = mock(Path.class);
        String horizon = "2025-2026";
        String area = "FR";
        ThermalClusterCapacityDto mockEntities = ThermalClusterCapacityDto.builder()
                .thermalClusterCapacities(List.of(ThermalClusterCapacityEntity.builder().build()))
                .warningMessage(WarningMessageEntity.builder().build())
                .build();

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
        when(areaRepository.findAllByStudyId(any())).thenReturn(List.of(AreaEntity.builder().id(1).name("FR").build()));

        var horizon = "2025-2026";
        thermalFileProcessorService.processThermalCapacityFile(tempFile, horizon, thermalFileProcessorService.buildThermalClusterCapacityValuesList(tempFile, horizon, true,"FR","CCGT",1), TrajectoryType.THERMAL_CAPACITY,"FR", "CCGT");

        verify(trajectoryRepository, times(1)).save(any());
    }

    @Test
    void saveThermalCapacitiesTrajectory() {
        // Given
        TrajectoryEntity trajectory = new TrajectoryEntity();
        ThermalClusterCapacityDto mockEntities = ThermalClusterCapacityDto.builder()
                .thermalClusterCapacities(List.of(ThermalClusterCapacityEntity.builder().build()))
                .warningMessage(WarningMessageEntity.builder().build())
                .build();
        when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenReturn(trajectory);

        // When
        TrajectoryEntity result = thermalFileProcessorService.saveThermalTrajectory(trajectory, mockEntities, TrajectoryType.THERMAL_CAPACITY);

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
    void buildThermalClusterCapacityValuesList_shouldThrowTechnicalExceptionWhenIOExceptionOccurs() throws IOException {
        Path mockPath = mock(Path.class);
        String horizon = "2025-2026";
        String area = "FR";
        String technology = "CCGT";
        when(areaRepository.findAllByStudyId(any())).thenReturn(List.of(AreaEntity.builder().id(1).name("FR").build()));

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.newInputStream(mockPath)).thenThrow(new IOException("File read error"));
            assertThrows(TechnicalException.class, () ->
                    thermalFileProcessorService.buildThermalClusterCapacityValuesList(mockPath, horizon, true, area, technology,1));
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
    void checkPowerAndNumberWithSameToUse_shouldThrowExceptionWhenInvalidGroupsExist() {
        List<ThermalClusterCapacityEntity> entities = List.of(
                ThermalClusterCapacityEntity.builder()
                        .area("FR")
                        .thermalClusterRef(ThermalClusterRef.builder().name("Cluster1").build())
                        .category(ThermalCategoryEnum.POWER)
                        .toUse(true)
                        .build(),
                ThermalClusterCapacityEntity.builder()
                        .area("FR")
                        .thermalClusterRef(ThermalClusterRef.builder().name("Cluster1").build())
                        .category(ThermalCategoryEnum.NUMBER)
                        .toUse(false)
                        .build()
        );

        BusinessException exception = assertThrows(BusinessException.class, () ->
                ThermalFileProcessorServiceImpl.checkPowerAndNumberWithSameToUse(entities)
        );

        assertTrue(exception.getMessage().contains("Les couples area/cluster suivants sont invalides"));
    }

    @Test
    void checkPowerAndNumberWithSameToUse_shouldNotThrowExceptionWhenAllGroupsAreValid() {
        List<ThermalClusterCapacityEntity> entities = List.of(
                ThermalClusterCapacityEntity.builder()
                        .area("FR")
                        .thermalClusterRef(ThermalClusterRef.builder().name("Cluster1").build())
                        .category(ThermalCategoryEnum.POWER)
                        .toUse(true)
                        .build(),
                ThermalClusterCapacityEntity.builder()
                        .area("FR")
                        .thermalClusterRef(ThermalClusterRef.builder().name("Cluster1").build())
                        .category(ThermalCategoryEnum.NUMBER)
                        .toUse(true)
                        .build()
        );

        assertDoesNotThrow(() ->
                ThermalFileProcessorServiceImpl.checkPowerAndNumberWithSameToUse(entities)
        );
    }

    @Test
    void checkPowerAndNumberWithSameToUse_shouldThrowExceptionWhenPowerCategoryIsMissing() {
        List<ThermalClusterCapacityEntity> entities = List.of(
                ThermalClusterCapacityEntity.builder()
                        .area("FR")
                        .thermalClusterRef(ThermalClusterRef.builder().name("Cluster1").build())
                        .category(ThermalCategoryEnum.NUMBER)
                        .toUse(true)
                        .build()
        );

        BusinessException exception = assertThrows(BusinessException.class, () ->
                ThermalFileProcessorServiceImpl.checkPowerAndNumberWithSameToUse(entities)
        );

        assertTrue(exception.getMessage().contains("Les couples area/cluster suivants sont invalides"));
    }

    @Test
    void checkPowerAndNumberWithSameToUse_shouldThrowExceptionWhenNumberCategoryIsMissing() {
        List<ThermalClusterCapacityEntity> entities = List.of(
                ThermalClusterCapacityEntity.builder()
                        .area("FR")
                        .thermalClusterRef(ThermalClusterRef.builder().name("Cluster1").build())
                        .category(ThermalCategoryEnum.POWER)
                        .toUse(true)
                        .build()
        );

        BusinessException exception = assertThrows(BusinessException.class, () ->
                ThermalFileProcessorServiceImpl.checkPowerAndNumberWithSameToUse(entities)
        );

        assertTrue(exception.getMessage().contains("Les couples area/cluster suivants sont invalides"));
    }

    @Test
    void checkPowerAndNumberWithSameToUse_shouldNotThrowExceptionWhenNoEntitiesExist() {
        List<ThermalClusterCapacityEntity> entities = List.of();

        assertDoesNotThrow(() ->
                ThermalFileProcessorServiceImpl.checkPowerAndNumberWithSameToUse(entities)
        );
    }
}
