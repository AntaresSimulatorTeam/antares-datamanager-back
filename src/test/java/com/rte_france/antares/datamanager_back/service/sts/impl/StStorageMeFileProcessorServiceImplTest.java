package com.rte_france.antares.datamanager_back.service.sts.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import com.rte_france.antares.datamanager_back.repository.model.StStorageEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StStorageMeFileProcessorServiceImplTest {

    @TempDir
    Path tempDir;

    private StStorageMeFileProcessorServiceImpl service;
    private TrajectoryRepository trajectoryRepository;
    private AntaresDataManagerProperties properties;
    private UserService userService;

    @BeforeEach
    void setUp() {
        properties = mock(AntaresDataManagerProperties.class);
        trajectoryRepository = mock(TrajectoryRepository.class);
        userService = mock(UserService.class);

        service = new StStorageMeFileProcessorServiceImpl(
                properties,
                trajectoryRepository,
                userService);

        when(properties.getNasDirectory()).thenReturn(tempDir.toString());
        when(properties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(properties.getStsMeDirectory()).thenReturn("ME/st_storage_ME/clusters");
        when(properties.getStsMeSeriesDirectory()).thenReturn("ME/st_storage_ME/series");

        AreaEntity areaEntity = new AreaEntity();
        areaEntity.setName("FR");

        // Mock default return for repository to avoid NPE
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(anyString(), anyString(), anyString()))
                .thenReturn(java.util.Optional.empty());
        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ============ Tests for missing sheet validation ============
    @Test
    void shouldThrowExceptionWhenSheetIsMissing() throws IOException {
        Path xlsx = createWorkbookWithoutSheet();
        placeInMeClusters(xlsx, "me_test.xlsx");

        assertThatThrownBy(() ->
                service.processStStorageMeFile("me_test", "2029-2030", 1)
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Missing horizon");
    }

    // ============ Tests for Node validation ============
    @Test
    void shouldThrowExceptionWhenNodeColumnHasNoValue() throws IOException {
        Path xlsx = createMeWorkbookWithEmptyNode();
        placeInMeClusters(xlsx, "me_test.xlsx");

        assertThatThrownBy(() ->
                service.processStStorageMeFile("me_test", "2029-2030", 1)
        ).isInstanceOf(BusinessException.class);
    }

    // ============ Tests for field length validations ============
    @Test
    void shouldThrowExceptionWhenNodeExceedsMaxLength() throws IOException {
        Path xlsx = createMeWorkbookWithLongNode(61);
        placeInMeClusters(xlsx, "me_test.xlsx");

        assertThatThrownBy(() ->
                service.processStStorageMeFile("me_test", "2029-2030", 1)
        ).isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldThrowExceptionWhenNameExceedsMaxLength() throws IOException {
        Path xlsx = createMeWorkbookWithLongName(41);
        placeInMeClusters(xlsx, "me_test.xlsx");

        assertThatThrownBy(() ->
                service.processStStorageMeFile("me_test", "2029-2030", 1)
        ).isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldThrowExceptionWhenGroupExceedsMaxLength() throws IOException {
        Path xlsx = createMeWorkbookWithLongGroup(21);
        placeInMeClusters(xlsx, "me_test.xlsx");

        assertThatThrownBy(() ->
                service.processStStorageMeFile("me_test", "2029-2030", 1)
        ).isInstanceOf(BusinessException.class);
    }

    // ============ Tests for numeric validation ============
    @Test
    void shouldThrowExceptionWhenNumericColumnsAreNonNumeric() throws IOException {
        Path xlsx = createMeWorkbookWithNonNumericValues();
        placeInMeClusters(xlsx, "me_test.xlsx");

        assertThatThrownBy(() ->
                service.processStStorageMeFile("me_test", "2029-2030", 1)
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("must be numeric");
    }

    @Test
    void shouldThrowExceptionWhenWithdrawalIsNonNumeric() throws IOException {
        Path xlsx = createMeWorkbookWithNonNumericWithdrawal();
        placeInMeClusters(xlsx, "me_test.xlsx");

        assertThatThrownBy(() ->
                service.processStStorageMeFile("me_test", "2029-2030", 1)
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("must be numeric");
    }

    @Test
    void shouldThrowExceptionWhenStorageIsNonNumeric() throws IOException {
        Path xlsx = createMeWorkbookWithNonNumericStorage();
        placeInMeClusters(xlsx, "me_test.xlsx");

        assertThatThrownBy(() ->
                service.processStStorageMeFile("me_test", "2029-2030", 1)
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("must be numeric");
    }

    @Test
    void shouldThrowExceptionWhenEfficiencyIsNonNumeric() throws IOException {
        Path xlsx = createMeWorkbookWithNonNumericEfficiency();
        placeInMeClusters(xlsx, "me_test.xlsx");

        assertThatThrownBy(() ->
                service.processStStorageMeFile("me_test", "2029-2030", 1)
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("must be numeric");
    }

    // ============ Tests for initial level validation ============
    @Test
    void shouldThrowExceptionWhenInitialLevelBelowZero() throws IOException {
        Path xlsx = createMeWorkbookWithInvalidInitialLevel(-0.1);
        placeInMeClusters(xlsx, "me_test.xlsx");

        assertThatThrownBy(() ->
                service.processStStorageMeFile("me_test", "2029-2030", 1)
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("initial_level must be between 0 and 1");
    }

    @Test
    void shouldThrowExceptionWhenInitialLevelAboveOne() throws IOException {
        Path xlsx = createMeWorkbookWithInvalidInitialLevel(1.1);
        placeInMeClusters(xlsx, "me_test.xlsx");

        assertThatThrownBy(() ->
                service.processStStorageMeFile("me_test", "2029-2030", 1)
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("initial_level must be between 0 and 1");
    }

    // ============ Tests for boolean validation ============
    @Test
    void shouldThrowExceptionWhenInitialLevelOptimIsNonBoolean() throws IOException {
        Path xlsx = createMeWorkbookWithNonBooleanValues(8);
        placeInMeClusters(xlsx, "me_test.xlsx");

        assertThatThrownBy(() ->
                service.processStStorageMeFile("me_test", "2029-2030", 1)
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("must be boolean");
    }

    @Test
    void shouldThrowExceptionWhenEnabledIsNonBoolean() throws IOException {
        Path xlsx = createMeWorkbookWithNonBooleanValues(9);
        placeInMeClusters(xlsx, "me_test.xlsx");

        assertThatThrownBy(() ->
                service.processStStorageMeFile("me_test", "2029-2030", 1)
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("must be boolean");
    }

    @Test
    void shouldThrowExceptionWhenConstraintsFlagIsNonBoolean() throws IOException {
        Path xlsx = createMeWorkbookWithNonBooleanValues(11);
        placeInMeClusters(xlsx, "me_test.xlsx");

        assertThatThrownBy(() ->
                service.processStStorageMeFile("me_test", "2029-2030", 1)
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("must be boolean");
    }

    // ============ Tests for successful processing ============
    @Test
    void shouldSuccessfullyProcessValidStStorageMeFile() throws IOException {
        Path xlsx = createValidMeWorkbook();
        placeInMeClusters(xlsx, "me_test.xlsx");

        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setHorizon("2030");
        trajectory.setId(1);
        when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenReturn(trajectory);
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(anyString(), anyString(), anyString()))
                .thenReturn(java.util.Optional.empty());

        TrajectoryEntity result = service.processStStorageMeFile("me_test", "2029-2030", 1);

        assertThat(result).isNotNull();
        assertThat(result.getHorizon()).isEqualTo("2030");
        verify(trajectoryRepository).save(any(TrajectoryEntity.class));
    }

    @Test
    void shouldSuccessfullyProcessMeFileWithSeriesTrue() throws IOException {
        Path xlsx = createMeWorkbookWithSeries();
        placeInMeClusters(xlsx, "me_test.xlsx");

        Path seriesDir = tempDir
                .resolve("trajectories")
                .resolve("ME/st_storage_ME/series")
                .resolve("me_test");
        Files.createDirectories(seriesDir);
        Files.createFile(seriesDir.resolve("lower_curve.xlsx"));
        Files.createFile(seriesDir.resolve("Pmax_injection.xlsx"));
        Files.createFile(seriesDir.resolve("Pmax_soutirage.xlsx"));
        Files.createFile(seriesDir.resolve("upper_curve.xlsx"));

        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setHorizon("2030");
        trajectory.setId(1);

        when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenReturn(trajectory);
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(anyString(), anyString(), anyString()))
                .thenReturn(java.util.Optional.empty());

        TrajectoryEntity result = service.processStStorageMeFile("me_test", "2029-2030", 1);

        assertThat(result).isNotNull();
        verify(trajectoryRepository).save(any(TrajectoryEntity.class));
    }

    @Test
    void shouldSetTsPathWhenSeriesIsTrueAndFilesExist() throws IOException {
        Path xlsx = createMeWorkbookWithSeries();
        placeInMeClusters(xlsx, "me_test.xlsx");

        Path seriesDir = tempDir
                .resolve("trajectories")
                .resolve("ME/st_storage_ME/series")
                .resolve("me_test");
        Files.createDirectories(seriesDir);
        Files.createFile(seriesDir.resolve("lower_curve.xlsx"));
        Files.createFile(seriesDir.resolve("Pmax_injection.xlsx"));
        Files.createFile(seriesDir.resolve("Pmax_soutirage.xlsx"));
        Files.createFile(seriesDir.resolve("upper_curve.xlsx"));

        TrajectoryEntity returnTrajectory = new TrajectoryEntity();
        returnTrajectory.setHorizon("2030");

        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(anyString(), anyString(), anyString()))
                .thenReturn(java.util.Optional.empty());

        TrajectoryEntity result = service.processStStorageMeFile("me_test", "2029-2030", 1);

        assertThat(result).isNotNull();
        assertThat(result.getStStorageEntities()).isNotEmpty();
        StStorageEntity entity = result.getStStorageEntities().get(0);
        assertThat(entity.getSeries()).isTrue();
        assertThat(entity.getTsPath()).isNotNull();
        assertThat(entity.getTsPath()).contains("ME/st_storage_ME/series");
        assertThat(entity.getTsPath()).contains("me_test");
    }

    @Test
    void shouldThrowExceptionWhenSeriesRequiredFilesAreMissing() throws IOException {
        Path xlsx = createMeWorkbookWithSeries();
        placeInMeClusters(xlsx, "me_test.xlsx");

        // Series directory not fully created - missing some files
        Path seriesDir = tempDir
                .resolve("trajectories")
                .resolve("ME/st_storage_ME/series")
                .resolve("me_test");
        Files.createDirectories(seriesDir);
        Files.createFile(seriesDir.resolve("lower_curve.xlsx"));
        // Missing: Pmax_injection.xlsx, Pmax_soutirage.xlsx, upper_curve.xlsx

        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenThrow(BusinessException.class);

        assertThatThrownBy(() ->
                service.processStStorageMeFile("me_test", "2029-2030", 1)
        ).isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldSuccessfullyProcessMeFileWithMultipleRows() throws IOException {
        Path xlsx = createMeWorkbookWithMultipleRows();
        placeInMeClusters(xlsx, "me_test.xlsx");

        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setHorizon("2030");
        trajectory.setId(1);

        when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenReturn(trajectory);
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(anyString(), anyString(), anyString()))
                .thenReturn(java.util.Optional.empty());

        service.processStStorageMeFile("me_test", "2029-2030", 1);

        verify(trajectoryRepository).save(any(TrajectoryEntity.class));
    }

    @Test
    void shouldThrowExceptionWhenNoDataFound() throws IOException {
        Path xlsx = createMeWorkbookWithOnlyEmptyRows();
        placeInMeClusters(xlsx, "me_test.xlsx");

        assertThatThrownBy(() ->
                service.processStStorageMeFile("me_test", "2029-2030", 1)
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("No ST Storage data found");
    }

    @Test
    void shouldHandleStringBooleanValues() throws IOException {
        Path xlsx = createMeWorkbookWithStringBooleans();
        placeInMeClusters(xlsx, "me_test.xlsx");

        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setHorizon("2030");
        trajectory.setId(1);

        when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenReturn(trajectory);
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(anyString(), anyString(), anyString()))
                .thenReturn(java.util.Optional.empty());

        TrajectoryEntity result = service.processStStorageMeFile("me_test", "2029-2030", 1);

        assertThat(result).isNotNull();
        verify(trajectoryRepository).save(any(TrajectoryEntity.class));
    }

    // ============ Helper methods for creating test data ============

    private Path createWorkbookWithoutSheet() throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }

    private Path createMeWorkbookWithEmptyNode() throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("2030");
            Row header = s.createRow(0);
            String[] headers = {"Node", "Name", "Group", "Injection [MW]", "Withdrawal [MW]", "Storage [MWh]", "Efficiency", "initial_level", "initial_level_optim", "Series", "unused1", "constraints_flag"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            Row r = s.createRow(1);
            // Create cell 0 but leave it empty (no value set)
            r.createCell(0).setCellValue("");
            r.createCell(1).setCellValue("cluster1");
            r.createCell(2).setCellValue("g1");
            r.createCell(3).setCellValue(10);
            r.createCell(4).setCellValue(20);
            r.createCell(5).setCellValue(30);
            r.createCell(6).setCellValue(0.9);
            r.createCell(7).setCellValue(0.5);
            r.createCell(8).setCellValue("false");
            r.createCell(9).setCellValue("false");
            r.createCell(10).setCellValue("false");
            r.createCell(11).setCellValue("false");

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }

    private Path createMeWorkbookWithLongNode(int length) throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("2030");
            Row header = s.createRow(0);
            String[] headers = {"Node", "Name", "Group", "Injection [MW]", "Withdrawal [MW]", "Storage [MWh]", "Efficiency", "initial_level", "initial_level_optim", "Series", "unused1", "constraints_flag"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            Row r = s.createRow(1);
            r.createCell(0).setCellValue("a".repeat(length));
            r.createCell(1).setCellValue("cluster1");
            r.createCell(2).setCellValue("g1");
            r.createCell(3).setCellValue(10);
            r.createCell(4).setCellValue(20);
            r.createCell(5).setCellValue(30);
            r.createCell(6).setCellValue(0.9);
            r.createCell(7).setCellValue(0.5);
            r.createCell(8).setCellValue("false");
            r.createCell(9).setCellValue("false");
            r.createCell(10).setCellValue("false");
            r.createCell(11).setCellValue("false");

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }

    private Path createMeWorkbookWithLongName(int length) throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("2030");
            Row header = s.createRow(0);
            String[] headers = {"Node", "Name", "Group", "Injection [MW]", "Withdrawal [MW]", "Storage [MWh]", "Efficiency", "initial_level", "initial_level_optim", "Series", "unused1", "constraints_flag"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            Row r = s.createRow(1);
            r.createCell(0).setCellValue("FR");
            r.createCell(1).setCellValue("a".repeat(length));
            r.createCell(2).setCellValue("g1");
            r.createCell(3).setCellValue(10);
            r.createCell(4).setCellValue(20);
            r.createCell(5).setCellValue(30);
            r.createCell(6).setCellValue(0.9);
            r.createCell(7).setCellValue(0.5);
            r.createCell(8).setCellValue("false");
            r.createCell(9).setCellValue("false");
            r.createCell(10).setCellValue("false");
            r.createCell(11).setCellValue("false");

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }

    private Path createMeWorkbookWithLongGroup(int length) throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("2030");
            Row header = s.createRow(0);
            String[] headers = {"Node", "Name", "Group", "Injection [MW]", "Withdrawal [MW]", "Storage [MWh]", "Efficiency", "initial_level", "initial_level_optim", "Series", "unused1", "constraints_flag"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            Row r = s.createRow(1);
            r.createCell(0).setCellValue("FR");
            r.createCell(1).setCellValue("cluster1");
            r.createCell(2).setCellValue("a".repeat(length));
            r.createCell(3).setCellValue(10);
            r.createCell(4).setCellValue(20);
            r.createCell(5).setCellValue(30);
            r.createCell(6).setCellValue(0.9);
            r.createCell(7).setCellValue(0.5);
            r.createCell(8).setCellValue("false");
            r.createCell(9).setCellValue("false");
            r.createCell(10).setCellValue("false");
            r.createCell(11).setCellValue("false");

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }

    private Path createMeWorkbookWithNonNumericValues() throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("2030");
            Row header = s.createRow(0);
            String[] headers = {"Node", "Name", "Group", "Injection [MW]", "Withdrawal [MW]", "Storage [MWh]", "Efficiency", "initial_level", "initial_level_optim", "Series", "unused1", "constraints_flag"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            Row r = s.createRow(1);
            r.createCell(0).setCellValue("FR");
            r.createCell(1).setCellValue("cluster1");
            r.createCell(2).setCellValue("g1");
            r.createCell(3).setCellValue("abc");
            r.createCell(4).setCellValue(20);
            r.createCell(5).setCellValue(30);
            r.createCell(6).setCellValue(0.9);
            r.createCell(7).setCellValue(0.5);
            r.createCell(8).setCellValue("false");
            r.createCell(9).setCellValue("false");
            r.createCell(10).setCellValue("false");
            r.createCell(11).setCellValue("false");

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }

    private Path createMeWorkbookWithNonNumericWithdrawal() throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("2030");
            Row header = s.createRow(0);
            String[] headers = {"Node", "Name", "Group", "Injection [MW]", "Withdrawal [MW]", "Storage [MWh]", "Efficiency", "initial_level", "initial_level_optim", "Series", "unused1", "constraints_flag"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            Row r = s.createRow(1);
            r.createCell(0).setCellValue("FR");
            r.createCell(1).setCellValue("cluster1");
            r.createCell(2).setCellValue("g1");
            r.createCell(3).setCellValue(10);
            r.createCell(4).setCellValue("abc");
            r.createCell(5).setCellValue(30);
            r.createCell(6).setCellValue(0.9);
            r.createCell(7).setCellValue(0.5);
            r.createCell(8).setCellValue("false");
            r.createCell(9).setCellValue("false");
            r.createCell(10).setCellValue("false");
            r.createCell(11).setCellValue("false");

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }

    private Path createMeWorkbookWithNonNumericStorage() throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("2030");
            Row header = s.createRow(0);
            String[] headers = {"Node", "Name", "Group", "Injection [MW]", "Withdrawal [MW]", "Storage [MWh]", "Efficiency", "initial_level", "initial_level_optim", "Series", "unused1", "constraints_flag"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            Row r = s.createRow(1);
            r.createCell(0).setCellValue("FR");
            r.createCell(1).setCellValue("cluster1");
            r.createCell(2).setCellValue("g1");
            r.createCell(3).setCellValue(10);
            r.createCell(4).setCellValue(20);
            r.createCell(5).setCellValue("xyz");
            r.createCell(6).setCellValue(0.9);
            r.createCell(7).setCellValue(0.5);
            r.createCell(8).setCellValue("false");
            r.createCell(9).setCellValue("false");
            r.createCell(10).setCellValue("false");
            r.createCell(11).setCellValue("false");

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }

    private Path createMeWorkbookWithNonNumericEfficiency() throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("2030");
            Row header = s.createRow(0);
            String[] headers = {"Node", "Name", "Group", "Injection [MW]", "Withdrawal [MW]", "Storage [MWh]", "Efficiency", "initial_level", "initial_level_optim", "Series", "unused1", "constraints_flag"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            Row r = s.createRow(1);
            r.createCell(0).setCellValue("FR");
            r.createCell(1).setCellValue("cluster1");
            r.createCell(2).setCellValue("g1");
            r.createCell(3).setCellValue(10);
            r.createCell(4).setCellValue(20);
            r.createCell(5).setCellValue(30);
            r.createCell(6).setCellValue("invalid");
            r.createCell(7).setCellValue(0.5);
            r.createCell(8).setCellValue("false");
            r.createCell(9).setCellValue("false");
            r.createCell(10).setCellValue("false");
            r.createCell(11).setCellValue("false");

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }

    private Path createMeWorkbookWithInvalidInitialLevel(double value) throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("2030");
            Row header = s.createRow(0);
            String[] headers = {"Node", "Name", "Group", "Injection [MW]", "Withdrawal [MW]", "Storage [MWh]", "Efficiency", "initial_level", "initial_level_optim", "Series", "unused1", "constraints_flag"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            Row r = s.createRow(1);
            r.createCell(0).setCellValue("FR");
            r.createCell(1).setCellValue("cluster1");
            r.createCell(2).setCellValue("g1");
            r.createCell(3).setCellValue(10);
            r.createCell(4).setCellValue(20);
            r.createCell(5).setCellValue(30);
            r.createCell(6).setCellValue(0.9);
            r.createCell(7).setCellValue(value);
            r.createCell(8).setCellValue("false");
            r.createCell(9).setCellValue("false");
            r.createCell(10).setCellValue("false");
            r.createCell(11).setCellValue("false");

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }

    private Path createMeWorkbookWithNonBooleanValues(int columnIndex) throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("2030");
            Row header = s.createRow(0);
            String[] headers = {"Node", "Name", "Group", "Injection [MW]", "Withdrawal [MW]", "Storage [MWh]", "Efficiency", "initial_level", "initial_level_optim", "Series", "unused1", "constraints_flag"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            Row r = s.createRow(1);
            r.createCell(0).setCellValue("FR");
            r.createCell(1).setCellValue("cluster1");
            r.createCell(2).setCellValue("g1");
            r.createCell(3).setCellValue(10);
            r.createCell(4).setCellValue(20);
            r.createCell(5).setCellValue(30);
            r.createCell(6).setCellValue(0.9);
            r.createCell(7).setCellValue(0.5);
            r.createCell(columnIndex).setCellValue("abc");
            r.createCell(9).setCellValue("false");
            r.createCell(10).setCellValue("false");
            r.createCell(11).setCellValue("false");

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }

    private Path createValidMeWorkbook() throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("2030");
            Row header = s.createRow(0);
            String[] headers = {"Node", "Name", "Group", "Injection [MW]", "Withdrawal [MW]", "Storage [MWh]", "Efficiency", "initial_level", "initial_level_optim", "Series", "unused1", "constraints_flag"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            Row r = s.createRow(1);
            r.createCell(0).setCellValue("FR");
            r.createCell(1).setCellValue("cluster1");
            r.createCell(2).setCellValue("g1");
            r.createCell(3).setCellValue(10);
            r.createCell(4).setCellValue(20);
            r.createCell(5).setCellValue(30);
            r.createCell(6).setCellValue(0.9);
            r.createCell(7).setCellValue(0.5);
            r.createCell(8).setCellValue("false");
            r.createCell(9).setCellValue("false");
            r.createCell(10).setCellValue("false");
            r.createCell(11).setCellValue("false");

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }

    private Path createMeWorkbookWithSeries() throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("2030");
            Row header = s.createRow(0);
            String[] headers = {"Node", "Name", "Group", "Injection [MW]", "Withdrawal [MW]", "Storage [MWh]", "Efficiency", "initial_level", "initial_level_optim", "unused1", "Series", "constraints_flag"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            Row r = s.createRow(1);
            r.createCell(0).setCellValue("FR");
            r.createCell(1).setCellValue("cluster1");
            r.createCell(2).setCellValue("g1");
            r.createCell(3).setCellValue(10);
            r.createCell(4).setCellValue(20);
            r.createCell(5).setCellValue(30);
            r.createCell(6).setCellValue(0.9);
            r.createCell(7).setCellValue(0.5);
            r.createCell(8).setCellValue("false");
            r.createCell(9).setCellValue("false");
            r.createCell(10).setCellValue("true");
            r.createCell(11).setCellValue("false");

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }

    private Path createMeWorkbookWithMultipleRows() throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("2030");
            Row header = s.createRow(0);
            String[] headers = {"Node", "Name", "Group", "Injection [MW]", "Withdrawal [MW]", "Storage [MWh]", "Efficiency", "initial_level", "initial_level_optim", "Series", "unused1", "constraints_flag"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            for (int rowNum = 1; rowNum <= 3; rowNum++) {
                Row r = s.createRow(rowNum);
                r.createCell(0).setCellValue("FR");
                r.createCell(1).setCellValue("cluster" + rowNum);
                r.createCell(2).setCellValue("g" + rowNum);
                r.createCell(3).setCellValue(10 + rowNum);
                r.createCell(4).setCellValue(20 + rowNum);
                r.createCell(5).setCellValue(30 + rowNum);
                r.createCell(6).setCellValue(0.8 + (rowNum * 0.01));
                r.createCell(7).setCellValue(0.3 + (rowNum * 0.1));
                r.createCell(8).setCellValue("false");
                r.createCell(9).setCellValue("false");
                r.createCell(10).setCellValue("false");
                r.createCell(11).setCellValue("false");
            }

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }

    private Path createMeWorkbookWithOnlyEmptyRows() throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("2030");
            Row header = s.createRow(0);
            String[] headers = {"Node", "Name", "Group", "Injection [MW]", "Withdrawal [MW]", "Storage [MWh]", "Efficiency", "initial_level", "initial_level_optim", "Series", "unused1", "constraints_flag"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            s.createRow(1);

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }

    private Path createMeWorkbookWithStringBooleans() throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("2030");
            Row header = s.createRow(0);
            String[] headers = {"Node", "Name", "Group", "Injection [MW]", "Withdrawal [MW]", "Storage [MWh]", "Efficiency", "initial_level", "initial_level_optim", "Series", "unused1", "constraints_flag"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            Row r = s.createRow(1);
            r.createCell(0).setCellValue("FR");
            r.createCell(1).setCellValue("cluster1");
            r.createCell(2).setCellValue("g1");
            r.createCell(3).setCellValue(10);
            r.createCell(4).setCellValue(20);
            r.createCell(5).setCellValue(30);
            r.createCell(6).setCellValue(0.9);
            r.createCell(7).setCellValue(0.5);
            r.createCell(8).setCellValue("true");
            r.createCell(9).setCellValue("true");
            r.createCell(10).setCellValue("false");
            r.createCell(11).setCellValue("false");

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }

    private void placeInMeClusters(Path file, String fileName) throws IOException {
        Path clustersDir = tempDir
                .resolve("trajectories")
                .resolve("ME/st_storage_ME/clusters");
        Files.createDirectories(clustersDir);
        Files.copy(file, clustersDir.resolve(fileName));
    }
}


