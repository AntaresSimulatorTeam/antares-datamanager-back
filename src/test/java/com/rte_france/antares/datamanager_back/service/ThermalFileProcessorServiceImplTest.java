package com.rte_france.antares.datamanager_back.service;


import com.rte_france.antares.datamanager_back.dto.ThermalClusterCapacityDto;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.impl.ThermalFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.impl.UserService;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.rte_france.antares.datamanager_back.service.impl.ThermalFileProcessorServiceImpl.REQUIRED_COMMON_PARAM_HEADER_COLUMNS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ThermalFileProcessorServiceImplTest {
    private static final String HORIZON_SHEET = "2025";
    private static final String THERMAL_PARAMETERS_FILE_NAME = "thermal_common_parameters_test.xlsx";
    private static final String THERMAL_CAPACITY_FILE_NAME = "thermal_BE_PEMMDB23_26avril";
    private static final String TRAJECTORY_NAME = "trajectory_test";

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

    @Mock
    private StudyRepository studyRepository;

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

    private static byte[] generateCommonParametersExcelFile(String horizonSheetName) throws IOException {
        try (var outputStream = new ByteArrayOutputStream();
             var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet(horizonSheetName);
            createHeader(sheet);
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
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(any(), any(), any())).thenReturn(Optional.of(trajectoryEntity));
        when(thermalTechnologyRepository.findThermalTechnologyByName(any())).thenReturn(Optional.of(ThermalTechnology.builder().name("CCGT").build()));
        when(thermalClusterRefRepository.findAll()).thenReturn(List.of(ThermalClusterRef.builder().name("Cluster1").thermalTechnology(ThermalTechnology.builder().name("CCGT").build()).build()));
        when(trajectoryRepository.save(any())).thenReturn(trajectoryEntity);
        when(areaRepository.findAllByStudyId(any())).thenReturn(List.of(AreaEntity.builder().id(1).name("FR").build()));

        var horizon = "2025-2026";
        thermalFileProcessorService.processThermalCapacityFile(tempFile, horizon, thermalFileProcessorService.buildThermalClusterCapacityValuesList(tempFile, horizon, true, "FR", "CCGT", 1), TrajectoryType.THERMAL_CAPACITY, "FR", "CCGT");

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
        TrajectoryEntity result = thermalFileProcessorService.saveThermalCapacitiesTrajectory(trajectory, mockEntities, TrajectoryType.THERMAL_CAPACITY);

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
    void findOrCreateThermalClusterRef_whenExistingAndPemmdbIsNA_updatesAndSaves() {
        // Given existing ref in cache with NA PEMMDB
        ThermalTechnology tech = ThermalTechnology.builder().name("oil").build();
        ThermalClusterRef existing = ThermalClusterRef.builder()
                .name("ClusterOil")
                .thermalTechnology(tech)
                .namePemmdb("NA")
                .build();
        when(thermalClusterRefRepository.findAll()).thenReturn(List.of(existing));
        when(thermalClusterRefRepository.save(any(ThermalClusterRef.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When
        ThermalClusterRef result = thermalFileProcessorService.findOrCreateThermalClusterRef("oil", "ClusterOil", "Oil-123");

        // Then
        assertSame(existing, result);
        assertEquals("Oil-123", result.getNamePemmdb());
        verify(thermalClusterRefRepository, times(1)).save(any(ThermalClusterRef.class));
        verifyNoInteractions(thermalTechnologyRepository);
    }

    @Test
    void findOrCreateThermalClusterRef_whenExistingAndPemmdbAlreadySet_doesNotOverwriteOrSave() {
        // Given existing ref with non-NA PEMMDB
        ThermalTechnology tech = ThermalTechnology.builder().name("CCGT").build();
        ThermalClusterRef existing = ThermalClusterRef.builder()
                .name("ClusterY")
                .thermalTechnology(tech)
                .namePemmdb("EXISTING-VAL")
                .build();
        when(thermalClusterRefRepository.findAll()).thenReturn(List.of(existing));

        // When
        ThermalClusterRef result = thermalFileProcessorService.findOrCreateThermalClusterRef("CCGT", "ClusterY", "NEW-VAL");

        // Then
        assertSame(existing, result);
        assertEquals("EXISTING-VAL", result.getNamePemmdb());
        verify(thermalClusterRefRepository, never()).save(any());
    }

    @Test
    void findOrCreateThermalClusterRef_whenCreating_setsProvidedPemmdbOrDefaultNA() {
        // First call: empty cache, no existing entries
        when(thermalClusterRefRepository.findAll()).thenReturn(List.of());
        ThermalTechnology tech = ThermalTechnology.builder().name("CCGT").build();
        when(thermalTechnologyRepository.findThermalTechnologyByName("CCGT"))
                .thenReturn(Optional.of(tech));
        when(thermalClusterRefRepository.save(any(ThermalClusterRef.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // With provided pemmdb
        ThermalClusterRef createdWithPemmdb = thermalFileProcessorService.findOrCreateThermalClusterRef("CCGT", "C1", "PEM1");
        assertEquals("C1", createdWithPemmdb.getName());
        assertEquals("PEM1", createdWithPemmdb.getNamePemmdb());
        assertEquals("CCGT", createdWithPemmdb.getThermalTechnology().getName());

        // With null pemmdb should default to NA (use a different name so it creates a new one)
        ThermalClusterRef createdWithDefault = thermalFileProcessorService.findOrCreateThermalClusterRef("CCGT", "C2", null);
        assertEquals("C2", createdWithDefault.getName());
        assertEquals("NA", createdWithDefault.getNamePemmdb());

        verify(thermalClusterRefRepository, atLeast(2)).save(any(ThermalClusterRef.class));
    }

    @Test
    void buildThermalClusterCapacityValuesList_shouldThrowTechnicalExceptionWhenIOExceptionOccurs() {
        Path mockPath = mock(Path.class);
        String horizon = "2025-2026";
        String area = "FR";
        String technology = "CCGT";
        when(areaRepository.findAllByStudyId(any())).thenReturn(List.of(AreaEntity.builder().id(1).name("FR").build()));

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.newInputStream(mockPath)).thenThrow(new IOException("File read error"));
            assertThrows(TechnicalException.class, () ->
                    thermalFileProcessorService.buildThermalClusterCapacityValuesList(mockPath, horizon, true, area, technology, 1));
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
                ThermalFileProcessorServiceImpl.checkPowerAndNumberWithSameToUse(entities, TRAJECTORY_NAME)
        );

        assertTrue(exception.getMessage().contains("must have same to_use value for power AND number category in THERMAL Installed Power"));
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
                ThermalFileProcessorServiceImpl.checkPowerAndNumberWithSameToUse(entities, TRAJECTORY_NAME)
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
                ThermalFileProcessorServiceImpl.checkPowerAndNumberWithSameToUse(entities, TRAJECTORY_NAME)
        );

        assertTrue(exception.getMessage().contains("must have power AND number category in THERMAL Installed Power trajectory"));
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
                ThermalFileProcessorServiceImpl.checkPowerAndNumberWithSameToUse(entities, TRAJECTORY_NAME)
        );

        assertTrue(exception.getMessage().contains("must have power AND number category in THERMAL Installed Power trajectory "));
    }

    @Test
    void checkPowerAndNumberWithSameToUse_shouldNotThrowExceptionWhenNoEntitiesExist() {
        List<ThermalClusterCapacityEntity> entities = List.of();

        assertDoesNotThrow(() ->
                ThermalFileProcessorServiceImpl.checkPowerAndNumberWithSameToUse(entities, TRAJECTORY_NAME)
        );
    }

    @Test
    void handleChecksumAndVersion_shouldThrowExceptionWhenChecksumMatchesExistingTrajectory() {
        ThermalClusterCapacityDto dto = new ThermalClusterCapacityDto();
        TrajectoryEntity existingTrajectory = new TrajectoryEntity();
        existingTrajectory.setChecksum("existingChecksum");
        Optional<TrajectoryEntity> existingTrajectoryOptional = Optional.of(existingTrajectory);
        String checksum = "existingChecksum";
        Path path = Path.of("testFile.xlsx");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                thermalFileProcessorService.handleChecksumAndVersion(dto, existingTrajectoryOptional, checksum, path)
        );

        assertTrue(exception.getMessage().contains("File already processed with same content"));
    }

    @Test
    void handleChecksumAndVersion_shouldUpdateVersionWhenChecksumDiffersFromExistingTrajectory() {
        ThermalClusterCapacityDto dto = new ThermalClusterCapacityDto();
        TrajectoryEntity existingTrajectory = new TrajectoryEntity();
        existingTrajectory.setChecksum("existingChecksum");
        existingTrajectory.setVersion(2);
        Optional<TrajectoryEntity> existingTrajectoryOptional = Optional.of(existingTrajectory);
        String checksum = "newChecksum";
        Path path = Path.of("testFile.xlsx");

        thermalFileProcessorService.handleChecksumAndVersion(dto, existingTrajectoryOptional, checksum, path);

        assertEquals("newChecksum", dto.getChecksum());
        assertEquals(3, dto.getVersion());
    }

    @Test
    void handleChecksumAndVersion_shouldSetVersionToOneWhenNoExistingTrajectory() {
        ThermalClusterCapacityDto dto = new ThermalClusterCapacityDto();
        Optional<TrajectoryEntity> existingTrajectoryOptional = Optional.empty();
        String checksum = "newChecksum";
        Path path = Path.of("testFile.xlsx");

        thermalFileProcessorService.handleChecksumAndVersion(dto, existingTrajectoryOptional, checksum, path);

        assertEquals("newChecksum", dto.getChecksum());
        assertEquals(1, dto.getVersion());
    }

    @Test
    void buildWarningMessage_shouldReturnWarningWhenMissingAreasExist() {
        Path path = Path.of("testFile.xlsx");
        String area = "OTHERS";
        Integer studyId = 1;
        boolean isSpecificAreaFound = false;
        Set<String> listOfOtherArea = Set.of("FR", "DE");
        List<String> studyAreas = List.of("FR", "DE", "IT");

        when(studyRepository.findById(studyId)).thenReturn(Optional.of(StudyEntity.builder().id(studyId).build()));
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());

        WarningMessageEntity result = thermalFileProcessorService.buildWarningMessage(path, area, studyId, isSpecificAreaFound, listOfOtherArea, studyAreas);

        assertNotNull(result);
        assertTrue(result.getWarningContent().contains("The following areas are missing"));
        assertTrue(result.getWarningContent().contains("IT"));
        assertEquals(WarningLevel.WARNING_LEVEL, result.getWarningLevel());
        verify(studyRepository).findById(studyId);
    }

    @Test
    void buildWarningMessage_shouldNotReturnWarningWhenAllAreasArePresent() {
        Path path = Path.of("testFile.xlsx");
        String area = "OTHERS";
        Integer studyId = 1;
        boolean isSpecificAreaFound = false;
        Set<String> listOfOtherArea = Set.of("FR", "DE", "IT");
        List<String> studyAreas = List.of("FR", "DE", "IT");

        WarningMessageEntity result = thermalFileProcessorService.buildWarningMessage(path, area, studyId, isSpecificAreaFound, listOfOtherArea, studyAreas);

        assertNull(result.getWarningContent());
        verifyNoInteractions(studyRepository);
    }

    @Test
    void buildWarningMessage_shouldThrowExceptionWhenStudyNotFound() {
        Path path = Path.of("testFile.xlsx");
        String area = "OTHERS";
        Integer studyId = 1;
        boolean isSpecificAreaFound = false;
        Set<String> listOfOtherArea = Set.of("FR", "DE");
        List<String> studyAreas = List.of("FR", "DE", "IT");

        when(studyRepository.findById(studyId)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () ->
                thermalFileProcessorService.buildWarningMessage(path, area, studyId, isSpecificAreaFound, listOfOtherArea, studyAreas)
        );

        assertTrue(exception.getMessage().contains("Study not found with id"));
        verify(studyRepository).findById(studyId);
    }

    @Test
    void buildWarningMessage_shouldThrowExceptionWhenSpecificAreaNotFound() {
        Path path = Path.of("testFile.xlsx");
        String area = "FR";
        Integer studyId = 1;
        boolean isSpecificAreaFound = false;
        Set<String> listOfOtherArea = Set.of();
        List<String> studyAreas = List.of("FR");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                thermalFileProcessorService.buildWarningMessage(path, area, studyId, isSpecificAreaFound, listOfOtherArea, studyAreas)
        );

        assertTrue(exception.getMessage().contains("No area of the AREA trajectory is present"));
        verifyNoInteractions(studyRepository);
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
        Integer studyId = 1;
        Path file = mockExcelFile(tempDir, THERMAL_PARAMETERS_FILE_NAME, () -> {
            try (var contentStream = new ByteArrayOutputStream(); var wb = new XSSFWorkbook()) {
                var sheet = wb.createSheet(HORIZON_SHEET);
                // Crée le header (ligne 0) avec toutes les cellules nécessaires
                createHeader(sheet);
                // Crée les 4 lignes ignorées
                // Crée la ligne de données à parser
                var row = sheet.createRow(5);
                row.createCell(0).setCellValue("pemmdb_name");
                row.createCell(1).setCellValue("ClusterA");
                row.createCell(2).setCellValue(1.0);
                row.createCell(3).setCellValue("GAS");
                row.createCell(4).setCellValue("CCGT");
                row.createCell(5).setCellValue("40-60");
                row.createCell(6).setCellValue(0.55);
                row.createCell(7).setCellValue(370.0);
                row.createCell(8).setCellValue(2.3);
                row.createCell(9).setCellValue(4.0);
                row.createCell(10).setCellValue(3.0);
                row.createCell(11).setCellValue(10.0);
                row.createCell(12).setCellValue(100.0);
                row.createCell(13).setCellValue(12.0);
                row.createCell(14).setCellValue(120.0);
                row.createCell(15).setCellValue(8.0);
                row.createCell(16).setCellValue(80.0);
                row.createCell(17).setCellValue(2.0);
                row.createCell(18).setCellValue(5.0);
                row.createCell(19).setCellValue(1.0);
                row.createCell(20).setCellValue(0.02);
                row.createCell(21).setCellValue(10.0);
                row.createCell(22).setCellValue(3.0);
                row.createCell(23).setCellValue(1.0);
                row.createCell(24).setCellValue(15.0);
                row.createCell(25).setCellValue(20.0);
                row.createCell(26).setCellValue(21.0);
                row.createCell(27).setCellValue(0.0);
                row.createCell(28).setCellValue(1.0);
                wb.write(contentStream);
                return contentStream.toByteArray();
            }
        });

        when(trajectoryRepository.findAllByStudyIdAndHorizonAndTypeOrderByVersionDesc(any(), any(), any())).thenReturn(List.of(TrajectoryEntity.builder().build()));
        when(thermalClusterRefRepository.findAll()).thenReturn(List.of());
        ThermalTechnology tech = ThermalTechnology.builder().name("CCGT").build();
        when(thermalTechnologyRepository.findThermalTechnologyByName("CCGT")).thenReturn(Optional.of(tech));
        when(thermalClusterRefRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var list = thermalFileProcessorService.buildThermalCommonParameterValuesList(file, HORIZON_SHEET, studyId);
        assertEquals(1, list.size());
        ThermalCommonParameterEntity e = list.get(0);
        assertNotNull(e.getThermalClusterRef());
        assertEquals("ClusterA", e.getThermalClusterRef().getName());
        assertEquals(0.55, e.getEfficiencyDefault());
        assertEquals(370.0, e.getCo2());
    }

    private static void createHeader(XSSFSheet sheet) {
        var header = sheet.createRow(4);
        for (int i = 0; i < REQUIRED_COMMON_PARAM_HEADER_COLUMNS.size(); i++) {
            header.createCell(i).setCellValue(REQUIRED_COMMON_PARAM_HEADER_COLUMNS.get(i));
        }
    }


    @Test
    void buildThermalCommonParameterValuesList_shouldThrowTechnicalExceptionWhenHorizonSheetMissing(@TempDir Path tempDir) throws Exception {
        Path file = mockExcelFile(tempDir, THERMAL_PARAMETERS_FILE_NAME, () -> generateCommonParametersExcelFile("OTHER_SHEET"));
        TechnicalException ex = assertThrows(TechnicalException.class, () ->
                thermalFileProcessorService.buildThermalCommonParameterValuesList(file, HORIZON_SHEET, 1)
        );
        assertTrue(ex.getMessage().contains("Missing suitable sheet for horizon"));
    }

    @Test
    void buildThermalCommonParameterValuesList_shouldThrowBusinessExceptionWhenNoDataFound(@TempDir Path tempDir) throws Exception {
        Path file = mockExcelFile(tempDir, THERMAL_PARAMETERS_FILE_NAME, () -> generateCommonParametersExcelFile(HORIZON_SHEET));
        when(trajectoryRepository.findAllByStudyIdAndHorizonAndTypeOrderByVersionDesc(any(), any(), any())).thenReturn(List.of(
                TrajectoryEntity.builder().thermalClusterCapacities(List.of(
                        ThermalClusterCapacityEntity.builder()
                                .thermalClusterRef(ThermalClusterRef.builder().name("ClusterA").build())
                                .build()
                )).build()
        ));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                thermalFileProcessorService.buildThermalCommonParameterValuesList(file, HORIZON_SHEET, 1)
        );

        assertTrue(exception.getMessage().contains("No data found from line 6 in Common Param trajectory"));
    }

    @Test
    void buildThermalCommonParameterValuesList_shouldThrowBusinessExceptionWhenClustersAreMissing(@TempDir Path tempDir) throws Exception {
        Path file = mockExcelFile(tempDir, THERMAL_PARAMETERS_FILE_NAME, () -> {
            try (var contentStream = new ByteArrayOutputStream(); var wb = new XSSFWorkbook()) {
                var sheet = wb.createSheet(HORIZON_SHEET);
                createHeader(sheet);
                var row = sheet.createRow(5);
                row.createCell(0).setCellValue("pemmdb_name");
                row.createCell(1).setCellValue("ClusterA");
                row.createCell(4).setCellValue("CCGT");
                wb.write(contentStream);
                return contentStream.toByteArray();
            }
        });

        when(trajectoryRepository.findAllByStudyIdAndHorizonAndTypeOrderByVersionDesc(any(), any(), any()))
                .thenReturn(List.of(TrajectoryEntity.builder().thermalClusterCapacities(List.of(
                        ThermalClusterCapacityEntity.builder()
                                .thermalClusterRef(ThermalClusterRef.builder().name("ClusterB").build())
                                .build()
                )).build()));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                thermalFileProcessorService.buildThermalCommonParameterValuesList(file, HORIZON_SHEET, 1)
        );

        assertTrue(exception.getMessage().contains("The following clusters are missing in the Common Parameters trajectory: ClusterB"));
    }

    @Test
    void buildThermalCommonParameterValuesList_shouldReturnThermalParametersWhenAllClustersArePresent(@TempDir Path tempDir) throws Exception {
        Path file = mockExcelFile(tempDir, THERMAL_PARAMETERS_FILE_NAME, () -> {
            try (var contentStream = new ByteArrayOutputStream(); var wb = new XSSFWorkbook()) {
                var sheet = wb.createSheet(HORIZON_SHEET);
                createHeader(sheet);
                var row = sheet.createRow(5);
                row.createCell(0).setCellValue("pemmdb_name");
                row.createCell(1).setCellValue("ClusterA");
                row.createCell(4).setCellValue("CCGT");
                wb.write(contentStream);
                return contentStream.toByteArray();
            }
        });

        when(trajectoryRepository.findAllByStudyIdAndHorizonAndTypeOrderByVersionDesc(any(), any(), any()))
                .thenReturn(List.of(TrajectoryEntity.builder().thermalClusterCapacities(List.of(
                        ThermalClusterCapacityEntity.builder()
                                .thermalClusterRef(ThermalClusterRef.builder().name("ClusterA").build())
                                .build()
                )).build()));

        var result = thermalFileProcessorService.buildThermalCommonParameterValuesList(file, HORIZON_SHEET, 1);

        assertEquals(1, result.size());
    }

    @Test
    void buildThermalCommonParameterValuesList_shouldThrowBusinessExceptionWhenNoDataFoundAndRowsExist(@TempDir Path tempDir) throws Exception {
        Path file = mockExcelFile(tempDir, THERMAL_PARAMETERS_FILE_NAME, () -> {
            try (var contentStream = new ByteArrayOutputStream(); var wb = new XSSFWorkbook()) {
                var sheet = wb.createSheet(HORIZON_SHEET);
                createHeader(sheet);
                // Ajouter des lignes vides après l'en-tête
                for (int i = 5; i < 10; i++) {
                    sheet.createRow(i);
                }
                wb.write(contentStream);
                return contentStream.toByteArray();
            }
        });

        BusinessException exception = assertThrows(BusinessException.class, () ->
                thermalFileProcessorService.buildThermalCommonParameterValuesList(file, HORIZON_SHEET, 1)
        );

        assertTrue(exception.getMessage().contains("No data found from line 6 in Common Param trajectory"));
    }

    @Test
    void buildThermalCommonParameterValuesList_shouldNotThrowExceptionWhenNoRowsExistAfterHeader(@TempDir Path tempDir) throws Exception {
        Path file = mockExcelFile(tempDir, THERMAL_PARAMETERS_FILE_NAME, () -> {
            try (var contentStream = new ByteArrayOutputStream(); var wb = new XSSFWorkbook()) {
                var sheet = wb.createSheet(HORIZON_SHEET);
                createHeader(sheet);
                wb.write(contentStream);
                return contentStream.toByteArray();
            }
        });

        BusinessException exception = assertThrows(BusinessException.class, () ->
                thermalFileProcessorService.buildThermalCommonParameterValuesList(file, HORIZON_SHEET, 1)
        );

        assertTrue(exception.getMessage().contains("No data found from line 6 in Common Param trajectory"));

    }

    @Test
    void buildThermalCommonParameterValuesList_shouldSkipRowsWithNullClusterName(@TempDir Path tempDir) throws Exception {
        Path file = mockExcelFile(tempDir, THERMAL_PARAMETERS_FILE_NAME, () -> {
            try (var contentStream = new ByteArrayOutputStream(); var wb = new XSSFWorkbook()) {
                var sheet = wb.createSheet(HORIZON_SHEET);
                createHeader(sheet);
                var row = sheet.createRow(5);
                row.createCell(1).setBlank(); // Null cluster name
                wb.write(contentStream);
                return contentStream.toByteArray();
            }
        });

        BusinessException exception = assertThrows(BusinessException.class, () ->
                thermalFileProcessorService.buildThermalCommonParameterValuesList(file, HORIZON_SHEET, 1)
        );

        assertTrue(exception.getMessage().contains("No data found from line 6 in Common Param trajectory"));

    }

    @Test
    void buildThermalCommonParameterValuesList_shouldSkipRowsWithEmptyClusterName(@TempDir Path tempDir) throws Exception {
        Path file = mockExcelFile(tempDir, THERMAL_PARAMETERS_FILE_NAME, () -> {
            try (var contentStream = new ByteArrayOutputStream(); var wb = new XSSFWorkbook()) {
                var sheet = wb.createSheet(HORIZON_SHEET);
                createHeader(sheet);
                var row = sheet.createRow(5);
                row.createCell(1).setCellValue(""); // Empty cluster name
                wb.write(contentStream);
                return contentStream.toByteArray();
            }
        });

        BusinessException exception = assertThrows(BusinessException.class, () ->
                thermalFileProcessorService.buildThermalCommonParameterValuesList(file, HORIZON_SHEET, 1)
        );

        assertTrue(exception.getMessage().contains("No data found from line 6 in Common Param trajectory"));
    }
}
