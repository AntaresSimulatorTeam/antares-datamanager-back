package com.rte_france.antares.datamanager_back.service.thermal_me.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.ThermalMeEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.rte_france.antares.datamanager_back.util.Utils.computeChecksumByType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ThermalMeFileProcessorServiceImpl Tests")
class ThermalMeFileProcessorServiceImplTest {

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private UserService userService;

    @Mock
    private AntaresDataManagerProperties antaresDataManagerProperties;

    @InjectMocks
    private ThermalMeFileProcessorServiceImpl service;

    @TempDir
    Path tempDir;

    private Path nasDir;
    private Path trajectoryFilePath;

    private static final String[] HEADERS = {
            "node", "group_name", "cluster_name", "enabled",
            "nominal_capacity", "nb_unit", "marginal_cost", "marginal_cost_timestep",
            "marginal_cost_modulation", "market_bid_cost", "market_bid_cost_timestep", "market_bid_cost_modulation",
            "must_run", "mr_timestep", "mr_modulation", "cm_timestep", "cm_modulation"
    };

    @BeforeEach
    void setUp() throws IOException {
        nasDir = tempDir.resolve("nas");
        trajectoryFilePath = nasDir.resolve("trajectories").resolve("thermal_me");
        Files.createDirectories(trajectoryFilePath);

        lenient().when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir.toString());
        lenient().when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        lenient().when(antaresDataManagerProperties.getThermalMeDirectory()).thenReturn("thermal_me");
        lenient().when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("USER123").build());
    }

    @Test
    @DisplayName("processThermalMeFile - should delegate to saveThermalMeTrajectoryInDb and return trajectory")
    void processThermalMeFile_success() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        createValidThermalMeExcelFile(trajectoryName, horizon, "annual");

        TrajectoryEntity savedEntity = createTrajectoryEntity(trajectoryName, horizon, 1);
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                eq(trajectoryName), eq(horizon), eq(TrajectoryType.THERMAL_CAPACITY_ME.name())))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenReturn(savedEntity);

        TrajectoryEntity result = service.processThermalMeFile(trajectoryName, horizon, 1);

        assertNotNull(result);
        assertEquals(trajectoryName, result.getFileName());
        assertEquals(horizon, result.getHorizon());
        assertEquals(1, result.getVersion());
        verify(trajectoryRepository, times(1)).save(any(TrajectoryEntity.class));
    }

    @Test
    @DisplayName("saveThermalMeTrajectoryInDb - should throw BusinessException when horizon is null")
    void saveThermalMeTrajectoryInDb_withNullHorizon_throwsException() {
        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.saveThermalMeTrajectoryInDb("test_trajectory", null)
        );

        assertEquals("Horizon must not be null", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    @DisplayName("saveThermalMeTrajectoryInDb - should throw BusinessException when trajectory name exceeds 40 characters")
    void saveThermalMeTrajectoryInDb_withTrajectoryNameExceeding40Chars_throwsException() {
        String longTrajectoryName = "a".repeat(41);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.saveThermalMeTrajectoryInDb(longTrajectoryName, "2024-2025")
        );

        assertEquals("Trajectory name cannot exceed 40 characters", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    @DisplayName("saveThermalMeTrajectoryInDb - should throw BusinessException when current user details is null")
    void saveThermalMeTrajectoryInDb_withNullUserDetails_throwsException() {
        when(userService.getCurrentUserDetails()).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.saveThermalMeTrajectoryInDb("test_trajectory", "2024-2025")
        );

        assertEquals("User NNI could not be determined", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    @DisplayName("saveThermalMeTrajectoryInDb - should throw BusinessException when user NNI is null")
    void saveThermalMeTrajectoryInDb_withNullUserNni_throwsException() {
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni(null).build());

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.saveThermalMeTrajectoryInDb("test_trajectory", "2024-2025")
        );

        assertEquals("User NNI could not be determined", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    @DisplayName("saveThermalMeTrajectoryInDb - should throw BusinessException when Antares path config is incomplete")
    void saveThermalMeTrajectoryInDb_incompletePathConfiguration_throwsException() {
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.saveThermalMeTrajectoryInDb("test_trajectory", "2024-2025")
        );

        assertEquals("Antares path configuration is incomplete", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    @DisplayName("saveThermalMeTrajectoryInDb - should throw IOException when path traversal is attempted")
    void saveThermalMeTrajectoryInDb_pathTraversal_throwsException() {
        String trajectoryName = "../../../etc/passwd";

        assertThrows(IOException.class, () ->
                service.saveThermalMeTrajectoryInDb(trajectoryName, "2024-2025")
        );
    }

    @Test
    @DisplayName("saveThermalMeTrajectoryInDb - should throw BusinessException when horizon sheet is missing")
    void saveThermalMeTrajectoryInDb_missingHorizonSheet_throwsException() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        createExcelFileWithWrongSheet(trajectoryName, "wrong_sheet");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.saveThermalMeTrajectoryInDb(trajectoryName, horizon)
        );

        assertEquals("Missing horizon {0} in {1} trajectory {2}", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertThat(exception.getErrorMessageArguments()).containsExactly("2025", "THERMAL_ME", trajectoryName);
    }

    @Test
    @DisplayName("saveThermalMeTrajectoryInDb - should throw BusinessException when horizon sheet is empty")
    void saveThermalMeTrajectoryInDb_emptyHorizonSheet_throwsException() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        createEmptyExcelFile(trajectoryName, "2025");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.saveThermalMeTrajectoryInDb(trajectoryName, horizon)
        );

        assertEquals("Missing horizon {0} in {1} trajectory {2}", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertThat(exception.getErrorMessageArguments()).containsExactly("2025", "THERMAL_ME", trajectoryName);
    }

    @Test
    @DisplayName("saveThermalMeTrajectoryInDb - should throw BusinessException when file already processed with same content")
    void saveThermalMeTrajectoryInDb_alreadyProcessedWithSameContent_throwsException() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        Path filePath = createValidThermalMeExcelFile(trajectoryName, horizon, "annual");

        String actualChecksum = computeChecksumByType(filePath, TrajectoryType.THERMAL_CAPACITY_ME, horizon, null);
        TrajectoryEntity existingTrajectory = createTrajectoryEntity(trajectoryName, horizon, 1);
        existingTrajectory.setChecksum(actualChecksum);

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                eq(trajectoryName), eq(horizon), eq(TrajectoryType.THERMAL_CAPACITY_ME.name())))
                .thenReturn(Optional.of(existingTrajectory));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.saveThermalMeTrajectoryInDb(trajectoryName, horizon)
        );

        assertEquals("File already processed with same content : {0}", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertThat(exception.getErrorMessageArguments()).containsExactly(trajectoryName);
    }

    @Test
    @DisplayName("saveThermalMeTrajectoryInDb - should increment version when file already exists with different content")
    void saveThermalMeTrajectoryInDb_createNewVersionWhenContentDifferent_success() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        createValidThermalMeExcelFile(trajectoryName, horizon, "annual");

        TrajectoryEntity existingTrajectory = createTrajectoryEntity(trajectoryName, horizon, 1);
        existingTrajectory.setChecksum("different_old_checksum");

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                eq(trajectoryName), eq(horizon), eq(TrajectoryType.THERMAL_CAPACITY_ME.name())))
                .thenReturn(Optional.of(existingTrajectory));
        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TrajectoryEntity result = service.saveThermalMeTrajectoryInDb(trajectoryName, horizon);

        assertNotNull(result);
        assertEquals(2, result.getVersion());
        verify(trajectoryRepository, times(1)).save(argThat(t -> t.getVersion() == 2));
    }

    @Test
    @DisplayName("saveThermalMeTrajectoryInDb - first upload with annual timesteps should parse all entity fields correctly")
    void saveThermalMeTrajectoryInDb_firstUploadWithAnnualTimesteps_success() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        createValidThermalMeExcelFile(trajectoryName, horizon, "annual");

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                eq(trajectoryName), eq(horizon), eq(TrajectoryType.THERMAL_CAPACITY_ME.name())))
                .thenReturn(Optional.empty());

        ArgumentCaptor<TrajectoryEntity> captor = ArgumentCaptor.forClass(TrajectoryEntity.class);
        when(trajectoryRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        TrajectoryEntity result = service.saveThermalMeTrajectoryInDb(trajectoryName, horizon);

        assertNotNull(result);
        assertEquals(1, result.getVersion());
        assertEquals(trajectoryName, result.getFileName());
        assertEquals(horizon, result.getHorizon());
        assertEquals(TrajectoryType.THERMAL_CAPACITY_ME.name(), result.getType());

        List<ThermalMeEntity> entities = result.getThermalMeEntities();
        assertNotNull(entities);
        assertEquals(1, entities.size());

        ThermalMeEntity entity = entities.get(0);
        assertEquals("FR", entity.getNode());
        assertEquals("nuclear_group", entity.getGroupName());
        assertEquals("cluster_1", entity.getClusterName());
        assertTrue(entity.getEnabled());
        assertEquals(900.0, entity.getNominalCapacity());
        assertEquals(2, entity.getNbUnit());
        assertEquals(45.5, entity.getMarginalCost());
        assertEquals("annual", entity.getMarginalCostTimestep());
        assertEquals(1, entity.getMarginalCostModulation());
        assertEquals(40.0, entity.getMarketBidCost());
        assertEquals("annual", entity.getMarketBidCostTimestep());
        assertEquals(1, entity.getMarketBidCostModulation());
        assertFalse(entity.getMustRun());
        assertEquals("annual", entity.getMrTimestep());
        assertEquals(0, entity.getMrModulation());
        assertEquals("annual", entity.getCmTimestep());
        assertEquals(1, entity.getCmModulation());
        assertEquals(result, entity.getTrajectory());
    }

    @Test
    @DisplayName("saveThermalMeTrajectoryInDb - first upload with hourly timesteps and modulation files present should succeed")
    void saveThermalMeTrajectoryInDb_firstUploadWithHourlyTimestepsAndModulationFilesPresent_success() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        createValidThermalMeExcelFile(trajectoryName, horizon, "hourly");

        createModulationFile("marginal_cost_modulation", trajectoryName);
        createModulationFile("market_bid_modulation", trajectoryName);
        createModulationFile("must_run", trajectoryName);
        createModulationFile("capacity_modulation", trajectoryName);

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                eq(trajectoryName), eq(horizon), eq(TrajectoryType.THERMAL_CAPACITY_ME.name())))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        TrajectoryEntity result = service.saveThermalMeTrajectoryInDb(trajectoryName, horizon);

        assertNotNull(result);
        assertEquals(1, result.getThermalMeEntities().size());
        assertEquals("hourly", result.getThermalMeEntities().get(0).getMarginalCostTimestep());
        assertEquals("hourly", result.getThermalMeEntities().get(0).getMarketBidCostTimestep());
        assertEquals("hourly", result.getThermalMeEntities().get(0).getMrTimestep());
        assertEquals("hourly", result.getThermalMeEntities().get(0).getCmTimestep());
    }

    @Test
    @DisplayName("saveThermalMeTrajectoryInDb - should throw BusinessException when node (col 0) is empty")
    void saveThermalMeTrajectoryInDb_missingFirstColumn_throwsException() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        createExcelFileWithSpecificCell(trajectoryName, horizon, 0, null);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.saveThermalMeTrajectoryInDb(trajectoryName, horizon)
        );

        assertEquals("{0} column must be filled in {1} trajectory {2}", exception.getMessage());
        assertThat(exception.getErrorMessageArguments()).containsExactly("node", "THERMAL_ME", trajectoryName);
    }

    @Test
    @DisplayName("saveThermalMeTrajectoryInDb - should throw BusinessException when node exceeds 60 characters")
    void saveThermalMeTrajectoryInDb_nodeExceedsMaxLength_throwsException() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        createExcelFileWithSpecificCell(trajectoryName, horizon, 0, "a".repeat(61));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.saveThermalMeTrajectoryInDb(trajectoryName, horizon)
        );

        assertEquals("{0} cannot exceed {1} characters in {2} trajectory {3}", exception.getMessage());
        assertThat(exception.getErrorMessageArguments()).containsExactly("node", "60", "THERMAL_ME", trajectoryName);
    }

    @Test
    @DisplayName("saveThermalMeTrajectoryInDb - should throw BusinessException when group_name exceeds 20 characters")
    void saveThermalMeTrajectoryInDb_groupNameExceedsMaxLength_throwsException() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        createExcelFileWithSpecificCell(trajectoryName, horizon, 1, "a".repeat(21));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.saveThermalMeTrajectoryInDb(trajectoryName, horizon)
        );

        assertEquals("{0} cannot exceed {1} characters in {2} trajectory {3}", exception.getMessage());
        assertThat(exception.getErrorMessageArguments()).containsExactly("group_name", "20", "THERMAL_ME", trajectoryName);
    }

    @Test
    @DisplayName("saveThermalMeTrajectoryInDb - should throw BusinessException when cluster_name exceeds 40 characters")
    void saveThermalMeTrajectoryInDb_clusterNameExceedsMaxLength_throwsException() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        createExcelFileWithSpecificCell(trajectoryName, horizon, 2, "a".repeat(41));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.saveThermalMeTrajectoryInDb(trajectoryName, horizon)
        );

        assertEquals("{0} cannot exceed {1} characters in {2} trajectory {3}", exception.getMessage());
        assertThat(exception.getErrorMessageArguments()).containsExactly("cluster_name", "40", "THERMAL_ME", trajectoryName);
    }

    @Test
    @DisplayName("saveThermalMeTrajectoryInDb - should throw BusinessException when enabled is not a boolean")
    void saveThermalMeTrajectoryInDb_enabledNotBoolean_throwsException() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        createExcelFileWithSpecificCell(trajectoryName, horizon, 3, "not_a_boolean");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.saveThermalMeTrajectoryInDb(trajectoryName, horizon)
        );

        assertEquals("{0} must be boolean in {1} trajectory {2}", exception.getMessage());
        assertThat(exception.getErrorMessageArguments()).containsExactly("enabled", "THERMAL_ME", trajectoryName);
    }

    @Test
    @DisplayName("saveThermalMeTrajectoryInDb - should throw BusinessException when must_run is not a boolean")
    void saveThermalMeTrajectoryInDb_mustRunNotBoolean_throwsException() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        createExcelFileWithSpecificCell(trajectoryName, horizon, 12, "not_a_boolean");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.saveThermalMeTrajectoryInDb(trajectoryName, horizon)
        );

        assertEquals("{0} must be boolean in {1} trajectory {2}", exception.getMessage());
        assertThat(exception.getErrorMessageArguments()).containsExactly("must_run", "THERMAL_ME", trajectoryName);
    }

    @ParameterizedTest
    @ValueSource(ints = {7, 10, 13, 15})
    @DisplayName("saveThermalMeTrajectoryInDb - should throw BusinessException when timestep is neither hourly nor annual")
    void saveThermalMeTrajectoryInDb_invalidTimestepValue_throwsException(int columnIndex) throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        createExcelFileWithSpecificCell(trajectoryName, horizon, columnIndex, "monthly");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.saveThermalMeTrajectoryInDb(trajectoryName, horizon)
        );

        assertEquals("Colunm {0} must be hourly or annual in {1} trajectory {2}", exception.getMessage());
        assertThat(exception.getErrorMessageArguments()).containsExactly(HEADERS[columnIndex], "THERMAL_ME", trajectoryName);
    }

    @Test
    @DisplayName("saveThermalMeTrajectoryInDb - should throw BusinessException when marginal_cost_timestep is hourly but file missing")
    void saveThermalMeTrajectoryInDb_hourlyMarginalCostMissingModulationFile_throwsException() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        createExcelFileWithSpecificCell(trajectoryName, horizon, 7, "hourly");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.saveThermalMeTrajectoryInDb(trajectoryName, horizon)
        );

        assertEquals("Missing {0} {1} TS in trajectory {2}", exception.getMessage());
        assertThat(exception.getErrorMessageArguments()).containsExactly("THERMAL_ME", "Marginal Cost Modulation", trajectoryName);
    }

    @Test
    @DisplayName("saveThermalMeTrajectoryInDb - should throw BusinessException when market_bid_cost_timestep is hourly but file missing")
    void saveThermalMeTrajectoryInDb_hourlyMarketBidCostMissingModulationFile_throwsException() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        createExcelFileWithSpecificCell(trajectoryName, horizon, 10, "hourly");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.saveThermalMeTrajectoryInDb(trajectoryName, horizon)
        );

        assertEquals("Missing {0} {1} TS in trajectory {2}", exception.getMessage());
        assertThat(exception.getErrorMessageArguments()).containsExactly("THERMAL_ME", "Market Bid Modulation", trajectoryName);
    }

    @Test
    @DisplayName("saveThermalMeTrajectoryInDb - should throw BusinessException when mr_timestep is hourly but file missing")
    void saveThermalMeTrajectoryInDb_hourlyMustRunMissingModulationFile_throwsException() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        createExcelFileWithSpecificCell(trajectoryName, horizon, 13, "hourly");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.saveThermalMeTrajectoryInDb(trajectoryName, horizon)
        );

        assertEquals("Missing {0} {1} TS in trajectory {2}", exception.getMessage());
        assertThat(exception.getErrorMessageArguments()).containsExactly("THERMAL_ME", "Must Run", trajectoryName);
    }

    @Test
    @DisplayName("saveThermalMeTrajectoryInDb - should throw BusinessException when cm_timestep is hourly but file missing")
    void saveThermalMeTrajectoryInDb_hourlyCapacityModulationMissingModulationFile_throwsException() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        createExcelFileWithSpecificCell(trajectoryName, horizon, 15, "hourly");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.saveThermalMeTrajectoryInDb(trajectoryName, horizon)
        );

        assertEquals("Missing {0} {1} TS in trajectory {2}", exception.getMessage());
        assertThat(exception.getErrorMessageArguments()).containsExactly("THERMAL_ME", "Capacity Modulation", trajectoryName);
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 5, 6, 8, 9, 11, 14, 16})
    @DisplayName("saveThermalMeTrajectoryInDb - should throw BusinessException when numeric column contains non-numeric text")
    void saveThermalMeTrajectoryInDb_nonNumericValue_throwsException(int columnIndex) throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";
        createExcelFileWithSpecificCell(trajectoryName, horizon, columnIndex, "invalid_num");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.saveThermalMeTrajectoryInDb(trajectoryName, horizon)
        );

        assertEquals("Column {0} must be numeric in {1} trajectory {2}", exception.getMessage());
        assertThat(exception.getErrorMessageArguments()).containsExactly(HEADERS[columnIndex], "THERMAL_ME", trajectoryName);
    }

    @Test
    @DisplayName("saveThermalMeTrajectoryInDb - should skip empty rows and header prior rows properly")
    void saveThermalMeTrajectoryInDb_skipsEmptyAndPriorRowsCorrectly() throws IOException {
        String trajectoryName = "test_trajectory";
        String horizon = "2024-2025";

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("2025");

        // Row 0-2: extra rows (e.g. metadata or comments)
        sheet.createRow(0).createCell(0).setCellValue("Title / metadata");
        sheet.createRow(1);
        sheet.createRow(2).createCell(0).setCellValue("Comment line");

        // Row 3: Headers
        Row headerRow = sheet.createRow(3);
        for (int i = 0; i < HEADERS.length; i++) {
            headerRow.createCell(i).setCellValue(HEADERS[i]);
        }

        // Row 4: Empty row
        sheet.createRow(4);

        // Row 5: Valid data row 1
        Row dataRow1 = sheet.createRow(5);
        fillDataRow(dataRow1, "FR", "group1", "cluster1", "annual");

        // Row 6: Empty row
        sheet.createRow(6);

        // Row 7: Valid data row 2
        Row dataRow2 = sheet.createRow(7);
        fillDataRow(dataRow2, "BE", "group2", "cluster2", "annual");

        // Populate extra rows up to 17 so getLastRowNum() >= 17 for complete header column list reading
        for (int i = 8; i <= 17; i++) {
            sheet.createRow(i);
        }

        saveWorkbook(workbook, trajectoryFilePath.resolve(trajectoryName + ".xlsx"));

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                eq(trajectoryName), eq(horizon), eq(TrajectoryType.THERMAL_CAPACITY_ME.name())))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        TrajectoryEntity result = service.saveThermalMeTrajectoryInDb(trajectoryName, horizon);

        assertNotNull(result);
        assertEquals(2, result.getThermalMeEntities().size());
        assertEquals("FR", result.getThermalMeEntities().get(0).getNode());
        assertEquals("BE", result.getThermalMeEntities().get(1).getNode());
    }

    @Test
    @DisplayName("saveThermalMeTrajectoryInDb - should throw TechnicalException when file cannot be read or is missing")
    void saveThermalMeTrajectoryInDb_ioExceptionReadingFile_throwsTechnicalException() {
        String trajectoryName = "non_existent_file";
        String horizon = "2024-2025";

        TechnicalException exception = assertThrows(TechnicalException.class, () ->
                service.saveThermalMeTrajectoryInDb(trajectoryName, horizon)
        );

        assertTrue(exception.getMessage().contains("Could not process THERMAL ME file"));
    }

    // ============ Helper Methods ============

    private Path createValidThermalMeExcelFile(String fileName, String horizon, String timestep) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        String horizonYear = horizon.split("-")[1];
        Sheet sheet = workbook.createSheet(horizonYear);

        // Row 0-2 (ignored)
        sheet.createRow(0);
        sheet.createRow(1);
        sheet.createRow(2);

        // Row 3: Headers
        Row headerRow = sheet.createRow(3);
        for (int i = 0; i < HEADERS.length; i++) {
            headerRow.createCell(i).setCellValue(HEADERS[i]);
        }

        // Row 4: Data
        Row dataRow = sheet.createRow(4);
        dataRow.createCell(0).setCellValue("FR");
        dataRow.createCell(1).setCellValue("nuclear_group");
        dataRow.createCell(2).setCellValue("cluster_1");
        dataRow.createCell(3).setCellValue(true);
        dataRow.createCell(4).setCellValue(900.0);
        dataRow.createCell(5).setCellValue(2);
        dataRow.createCell(6).setCellValue(45.5);
        dataRow.createCell(7).setCellValue(timestep);
        dataRow.createCell(8).setCellValue(1);
        dataRow.createCell(9).setCellValue(40.0);
        dataRow.createCell(10).setCellValue(timestep);
        dataRow.createCell(11).setCellValue(1);
        dataRow.createCell(12).setCellValue(false);
        dataRow.createCell(13).setCellValue(timestep);
        dataRow.createCell(14).setCellValue(0);
        dataRow.createCell(15).setCellValue(timestep);
        dataRow.createCell(16).setCellValue(1);

        // Populate empty rows up to row 17 to ensure sheet.getLastRowNum() >= 17 for full header extraction in row 3
        for (int r = 5; r <= 17; r++) {
            sheet.createRow(r);
        }

        Path filePath = trajectoryFilePath.resolve(fileName + ".xlsx");
        saveWorkbook(workbook, filePath);
        return filePath;
    }

    private void fillDataRow(Row dataRow, String node, String group, String cluster, String timestep) {
        dataRow.createCell(0).setCellValue(node);
        dataRow.createCell(1).setCellValue(group);
        dataRow.createCell(2).setCellValue(cluster);
        dataRow.createCell(3).setCellValue(true);
        dataRow.createCell(4).setCellValue(500.0);
        dataRow.createCell(5).setCellValue(1);
        dataRow.createCell(6).setCellValue(30.0);
        dataRow.createCell(7).setCellValue(timestep);
        dataRow.createCell(8).setCellValue(1);
        dataRow.createCell(9).setCellValue(25.0);
        dataRow.createCell(10).setCellValue(timestep);
        dataRow.createCell(11).setCellValue(1);
        dataRow.createCell(12).setCellValue(false);
        dataRow.createCell(13).setCellValue(timestep);
        dataRow.createCell(14).setCellValue(0);
        dataRow.createCell(15).setCellValue(timestep);
        dataRow.createCell(16).setCellValue(1);
    }

    private void createExcelFileWithSpecificCell(String fileName, String horizon, int targetCol, Object targetValue) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        String horizonYear = horizon.split("-")[1];
        Sheet sheet = workbook.createSheet(horizonYear);

        // Row 0-2 (ignored)
        sheet.createRow(0);
        sheet.createRow(1);
        sheet.createRow(2);

        // Row 3: Headers
        Row headerRow = sheet.createRow(3);
        for (int i = 0; i < HEADERS.length; i++) {
            headerRow.createCell(i).setCellValue(HEADERS[i]);
        }

        // Row 4: Data
        Row dataRow = sheet.createRow(4);
        dataRow.createCell(0).setCellValue("FR");
        dataRow.createCell(1).setCellValue("nuclear_group");
        dataRow.createCell(2).setCellValue("cluster_1");
        dataRow.createCell(3).setCellValue(true);
        dataRow.createCell(4).setCellValue(900.0);
        dataRow.createCell(5).setCellValue(2);
        dataRow.createCell(6).setCellValue(45.5);
        dataRow.createCell(7).setCellValue("annual");
        dataRow.createCell(8).setCellValue(1);
        dataRow.createCell(9).setCellValue(40.0);
        dataRow.createCell(10).setCellValue("annual");
        dataRow.createCell(11).setCellValue(1);
        dataRow.createCell(12).setCellValue(false);
        dataRow.createCell(13).setCellValue("annual");
        dataRow.createCell(14).setCellValue(0);
        dataRow.createCell(15).setCellValue("annual");
        dataRow.createCell(16).setCellValue(1);

        // Override target column
        Cell cell = dataRow.getCell(targetCol);
        if (targetValue == null) {
            dataRow.removeCell(cell);
        } else if (targetValue instanceof String str) {
            cell.setCellValue(str);
        } else if (targetValue instanceof Double d) {
            cell.setCellValue(d);
        } else if (targetValue instanceof Integer in) {
            cell.setCellValue(in);
        } else if (targetValue instanceof Boolean b) {
            cell.setCellValue(b);
        }

        for (int r = 5; r <= 17; r++) {
            sheet.createRow(r);
        }

        Path filePath = trajectoryFilePath.resolve(fileName + ".xlsx");
        saveWorkbook(workbook, filePath);
    }

    private void createExcelFileWithWrongSheet(String fileName, String sheetName) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(sheetName);
        sheet.createRow(0).createCell(0).setCellValue("Test");
        saveWorkbook(workbook, trajectoryFilePath.resolve(fileName + ".xlsx"));
    }

    private void createEmptyExcelFile(String fileName, String sheetName) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        workbook.createSheet(sheetName);
        saveWorkbook(workbook, trajectoryFilePath.resolve(fileName + ".xlsx"));
    }

    private void createModulationFile(String folderName, String trajectoryName) throws IOException {
        Path folder = trajectoryFilePath.resolve(folderName);
        Files.createDirectories(folder);
        Path modFile = folder.resolve(folderName + "_" + trajectoryName + ".xlsx");
        Files.createFile(modFile);
    }

    private void saveWorkbook(Workbook workbook, Path filePath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            workbook.write(fos);
        }
        workbook.close();
    }

    private TrajectoryEntity createTrajectoryEntity(String fileName, String horizon, int version) {
        return TrajectoryEntity.builder()
                .id(1)
                .fileName(fileName)
                .horizon(horizon)
                .version(version)
                .type(TrajectoryType.THERMAL_CAPACITY_ME.name())
                .createdBy("USER123")
                .fileSize(1024L)
                .creationDate(LocalDateTime.now())
                .lastModificationContentDate(LocalDateTime.now())
                .checksum("test_checksum")
                .build();
    }
}
