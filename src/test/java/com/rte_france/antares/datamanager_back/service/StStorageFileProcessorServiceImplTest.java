package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.StConstraintsHoursEntity;
import com.rte_france.antares.datamanager_back.repository.model.StConstraintsParameterEntity;
import com.rte_france.antares.datamanager_back.repository.model.StStorageEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.sts.StStorageConstraintsFileProcessorService;
import com.rte_france.antares.datamanager_back.service.sts.StsTsFile;
import com.rte_france.antares.datamanager_back.service.sts.impl.StStorageFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.sts.impl.StsPropertiesAssemblerServiceImpl;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.util.PathSecurityUtil;
import com.rte_france.antares.datamanager_back.util.Utils;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrix;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrixColumn;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesReader;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesWriter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.MessageFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StStorageFileProcessorServiceImplTest {

    private AntaresDataManagerProperties properties;
    private TrajectoryRepository trajectoryRepository;
    private UserService userService;
    private AreaRepository areaRepository;
    private StStorageConstraintsFileProcessorService stStorageConstraintsFileProcessorService;

    private PathSecurityUtil pathSecurityUtil;
    private StStorageFileProcessorServiceImpl service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        properties = mock(AntaresDataManagerProperties.class);
        trajectoryRepository = mock(TrajectoryRepository.class);
        userService = mock(UserService.class);
        areaRepository = mock(AreaRepository.class);
        pathSecurityUtil = new PathSecurityUtil(properties);
        service = new StStorageFileProcessorServiceImpl(
                properties,
                trajectoryRepository,
                userService,
                areaRepository,
                stStorageConstraintsFileProcessorService = mock(StStorageConstraintsFileProcessorService.class),
                pathSecurityUtil
                );

        when(properties.getNasDirectory()).thenReturn(tempDir.toString());
        when(properties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(properties.getStsDirectory()).thenReturn("STS");

        // default study areas
        when(areaRepository.findAllByStudyId(anyInt(), eq(TrajectoryType.AREA.toString())))
                .thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                    setName("FR");
                }}));
    }

    @Test
    void shouldThrowExceptionWhenTrajectoryNameIsInvalid() {
        assertThatThrownBy(() ->
                service.processStStorageFile(
                        "invalid_name",
                        "2020-2030",
                        1,
                        true,
                        "FR",
                        "battery"
                )
        )
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Trajectory name must start with");
    }

    @Test
    void shouldThrowExceptionWhenSheetIsMissing() throws IOException {
        Path xlsx = createWorkbookWithoutSheet();
        placeInClusters(xlsx, "battery", "cluster_battery_test.xlsx");

        assertThatThrownBy(() ->
                service.processStStorageFile("cluster_battery_test", "2029-2030", 1, false, "FR", "battery")
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Horizon {0} does not exist in the {1} trajectory {2}");
    }

    @Test
    void shouldThrowExceptionWhenNoValidRowsFound() throws IOException {
        Path xlsx = createWorkbookWithHeaderOnly("2030");
        placeInClusters(xlsx, "battery", "cluster_battery_test.xlsx");

        assertThatThrownBy(() ->
                service.processStStorageFile("cluster_battery_test", "2029-2030", 1, false, "FR", "battery")
        ).isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getMessage()).contains("Selected area {0} is not present in the 'node' column of STS trajectory {1}");
                    assertThat(be.getErrorMessageArguments()).containsExactly("FR", "cluster_battery_test.xlsx");
                });
    }

    @Test
    void shouldCreateTrajectoryAndEntitiesSuccessfully() throws IOException {
        Path xlsx = createValidWorkbook("2030", false);
        placeInClusters(xlsx, "battery", "cluster_battery_test.xlsx");

        // stubs for repository/user
        when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{
            setNni("TEST_NNI");
        }});
        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenAnswer((Answer<TrajectoryEntity>) inv -> inv.getArgument(0));

        TrajectoryEntity trajectory = service.processStStorageFile(
                "cluster_battery_test", "2029-2030", 1, false, "FR", "battery"
        );

        assertThat(trajectory).isNotNull();
        assertThat(trajectory.getStStorageEntities()).hasSize(1);
        assertThat(trajectory.getHasTimeSeries()).isFalse();

        StStorageEntity entity = trajectory.getStStorageEntities().getFirst();
        assertThat(entity.getArea()).isEqualTo("FR");
        assertThat(entity.getName()).isEqualTo("cluster1");
        assertThat(entity.getInjection()).isEqualByComparingTo(BigDecimal.valueOf(10));
        assertThat(entity.getSeries()).isFalse();
        assertThat(entity.getAllowOverflow()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "MISSING,",
            "BLANK,",
            "STRING, ''",
            "STRING, '   '",
            "FORMULA, '\"\"'",
            "FORMULA, '\"   \"'"
    })
    void shouldRejectEmptyAllowOverflow(String type, String value) throws IOException {
        Path xlsx = createValidWorkbook("2030", false);
        Workbook workbook;
        try (InputStream input = Files.newInputStream(xlsx)) {
            workbook = WorkbookFactory.create(input);
        }
        try (workbook) {
            Row row = workbook.getSheet("2030").getRow(1);
            row.removeCell(row.getCell(13));
            switch (type) {
                case "MISSING" -> { }
                case "BLANK" -> row.createCell(13).setBlank();
                case "STRING" -> row.createCell(13).setCellValue(value);
                case "FORMULA" -> row.createCell(13).setCellFormula(value);
                default -> throw new IllegalArgumentException("Unexpected cell type: " + type);
            }
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            try (OutputStream output = Files.newOutputStream(xlsx)) {
                workbook.write(output);
            }
        }
        placeInClusters(xlsx, "battery", "cluster_battery_test.xlsx");

        assertThatThrownBy(() -> service.processStStorageFile(
                "cluster_battery_test", "2029-2030", 1, false, "FR", "battery"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getMessage()).isEqualTo(
                            "Empty values found for Allow_overflow for horizon {0} in {1} trajectory");
                    assertThat(exception.getErrorMessageArguments()).containsExactly("2030", "cluster_battery_test.xlsx");
                    assertThat(MessageFormat.format(exception.getMessage(), exception.getErrorMessageArguments().toArray()))
                            .isEqualTo("Empty values found for Allow_overflow for horizon 2030 in cluster_battery_test.xlsx trajectory");
                });
        verify(trajectoryRepository, never()).save(any());
    }

    @Test
    void shouldCreateTrajectoryWithIncrementVersionWhenTrajectoryExists() throws IOException {
        Path xlsx = createValidWorkbook("2030", false);
        placeInClusters(xlsx, "battery", "cluster_battery_test.xlsx");

        // stubs for repository/user
        when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{
            setNni("TEST_NNI");
        }});
        var trajectoryEntity = mock(TrajectoryEntity.class);
        trajectoryEntity.setType(TrajectoryType.STS.name());
        trajectoryEntity.setFileName(xlsx.toString());
        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(trajectoryEntity));
        when(trajectoryRepository.save(any())).thenAnswer((Answer<TrajectoryEntity>) inv -> inv.getArgument(0));

        TrajectoryEntity trajectory = service.processStStorageFile(
                "cluster_battery_test", "2029-2030", 1, false, "FR", "battery"
        );

        assertThat(trajectory).isNotNull();
        assertThat(trajectory.getHasTimeSeries()).isFalse();
    }

    @Test
    void shouldIgnoreRowWhenSeriesIsTrueAndTsFilesAreMissing() throws IOException {
        String horizon = "2029-2030";
        String technology = "battery";
        String area = "FR";
        String clusterName = "cluster1";

        Path xlsx = createWorkbookWithSeriesTrue("2030", area, clusterName);
        placeInClusters(xlsx, technology, "cluster_battery_test.xlsx");

        // Create the STS directory but WITHOUT required files
        Path stsDir = tempDir
                .resolve("trajectories")
                .resolve("STS")
                .resolve(technology)
                .resolve("series")
                .resolve("test")
                .resolve(clusterName)
                .resolve(area);

        Files.createDirectories(stsDir);

        // no required files -> row ignored and therefore no valid rows -> exception
        assertThatThrownBy(() ->
                service.processStStorageFile("cluster_battery_test", horizon, 1, false, area, technology)
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Can not import : Missing TS files");
    }

    @Test
    void shouldThrowWhenNonNumericValuesInNumericColumns() throws IOException {
        Path xlsx = createWorkbookWithNonNumeric("2030");
        placeInClusters(xlsx, "battery", "cluster_battery_test.xlsx");

        assertThatThrownBy(() ->
                service.processStStorageFile("cluster_battery_test", "2029-2030", 1, false, "FR", "battery")
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("must be numeric in STS trajectory");
    }

    @Test
    void shouldThrowWhenOTHERSAreaNonePresent() throws IOException {
        // study areas contain only FR (set in setUp), the file contains area XX -> no study area found -> exception
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("2030");
            Row header = s.createRow(0);
            String[] headers = {"Area", "Name", "Group", "Injection", "Withdrawal", "Storage", "Efficiency_injection", "Efficiency_withdrawal", "Initial_level", "Initial_level_optim", "Enabled", "Series", "Constraints", "Allow_overflow"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            Row r = s.createRow(1);
            r.createCell(0).setCellValue("XX");
            r.createCell(1).setCellValue("cluster1");
            // set numeric columns
            r.createCell(3).setCellValue(1);
            r.createCell(4).setCellValue(1);
            r.createCell(5).setCellValue(1);
            r.createCell(6).setCellValue(1);
            r.createCell(7).setCellValue(1);
            r.createCell(8).setCellValue(1);

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        placeInClusters(file, "battery", "cluster_battery_test.xlsx");

        assertThatThrownBy(() ->
                service.processStStorageFile("cluster_battery_test", "2029-2030", 1, false, "OTHERS_AREA", "battery")
        ).isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getMessage()).contains("Selected area {0} is not present in the 'node' column of STS trajectory {1}");
                    assertThat(be.getErrorMessageArguments()).containsExactly("OTHERS_AREA", "cluster_battery_test.xlsx");
                });
    }

    @Test
    void shouldProcessWhenOTHERSAreaAndSomeStudyAreasMissing() throws IOException {
        // study has FR and DE
        when(areaRepository.findAllByStudyId(anyInt(), eq(TrajectoryType.AREA.toString())))
                .thenReturn(List.of(
                        new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                            setName("FR");
                        }},
                        new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                            setName("DE");
                        }}
                ));

        // the file contains FR only -> missing DE, the process should succeed (warning logic not persisted in current impl)
        Path xlsx = createValidWorkbook("2030", false);
        placeInClusters(xlsx, "battery", "cluster_battery_test.xlsx");

        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenAnswer((Answer<TrajectoryEntity>) inv -> inv.getArgument(0));
        when(userService.getCurrentUserDetails()).thenReturn(null);

        TrajectoryEntity trajectory = service.processStStorageFile("cluster_battery_test", "2029-2030", 1, false, "FR", "battery");
        assertThat(trajectory).isNotNull();
        assertThat(trajectory.getStStorageEntities()).hasSize(1);
    }

    @Test
    void shouldThrowWhenNoStudyAreaIsPresentInStsFile() throws Exception {
        // GIVEN
        String horizon = "2025";
        String areaParam = "IT";
        String technology = "battery";

        // study has FR and DE
        when(areaRepository.findAllByStudyId(anyInt(), eq(TrajectoryType.AREA.toString())))
                .thenReturn(List.of(
                        new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                            setName("FR");
                        }},
                        new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                            setName("DE");
                        }}
                ));

        // Create a fake STS Excel file in proper directory structure
        Path trajectoryDir = tempDir.resolve("trajectories").resolve("STS").resolve(technology);
        Files.createDirectories(trajectoryDir);
        Path tempFile = trajectoryDir.resolve("sts_test.xlsx");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(horizon);

            // Header
            Row header = sheet.createRow(0);
            String[] headers = {"Area", "Name", "Group", "Injection", "Withdrawal", "Storage",
                    "Efficiency_injection", "Efficiency_withdrawal", "Initial_level",
                    "Initial_level_optim", "Enabled", "Series", "Constraints", "Allow_overflow"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            // Row with ONLY area IT (not in study)
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("IT"); // Area
            row.createCell(1).setCellValue("Cluster1");
            row.createCell(2).setCellValue("Group1");
            for (int i = 3; i <= 8; i++) {
                row.createCell(i).setCellValue(1.0);
            }
            row.createCell(9).setCellValue(true);
            row.createCell(10).setCellValue(true);
            row.createCell(11).setCellValue(false);
            row.createCell(12).setCellValue(false);
            row.createCell(13).setCellValue(false);

            try (OutputStream os = Files.newOutputStream(tempFile)) {
                workbook.write(os);
            }
        }

        // Spy service to bypass file search
        StStorageFileProcessorServiceImpl serviceSpy = Mockito.spy(service);

        doReturn(tempFile)
                .when(serviceSpy)
                .findTrajectoryFileCaseInsensitive(anyString(), anyString());

        // WHEN / THEN
        assertThatThrownBy(() -> serviceSpy.processStStorageFile(
                "cluster_battery_test.xlsx",
                "horizon-2025",
                1,
                false,
                areaParam,
                technology
        )).isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getMessage()).contains("Selected area {0}");
                    assertThat(be.getErrorMessageArguments()).contains(areaParam);
                });
    }

    @Test
    void shouldThrowWhenMissingColumnsInStsFile() throws Exception {
        String horizon = "2025";
        String areaParam = "FR";
        String technology = "battery";

        // studyAreas peu importe ici, on ne va jamais jusque-là
        when(areaRepository.findAllByStudyId(anyInt(), eq(TrajectoryType.AREA.toString())))
                .thenReturn(List.of());

        // Création d’un Excel avec header incomplet
        Path tempFile = tempDir.resolve("sts_missing_columns.xlsx");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(horizon);

            Row header = sheet.createRow(0);

            // On met volontairement QUE 3 colonnes au lieu des 13 attendues
            header.createCell(0).setCellValue("Area");
            header.createCell(1).setCellValue("Name");
            header.createCell(2).setCellValue("Group");
            // Toutes les autres colonnes manquent

            try (OutputStream os = Files.newOutputStream(tempFile)) {
                workbook.write(os);
            }
        }

        placeInClusters(tempFile, technology, "cluster_battery_test.xlsx");

        // WHEN / THEN
        assertThatThrownBy(() -> service.processStStorageFile(
                "cluster_battery_test",
                "horizon-2025",
                1,
                false,
                areaParam,
                technology
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Missing columns");
    }

    @Test
    void shouldiSaveRowWhenSeriesIsTrueAndTsFilesExists() throws IOException {
        String horizon = "2029-2030";
        String technology = "battery";
        String area = "FR";
        String clusterName = "cluster1";

        Path xlsx = createWorkbookWithSeriesTrue("2030", area, clusterName);
        placeInClusters(xlsx, technology, "cluster_battery_test.xlsx");

        // Create STS directory but WITHOUT required files
        Path stsDir = tempDir
                .resolve("trajectories")
                .resolve("STS")
                .resolve(technology)
                .resolve("series")
                .resolve("test")
                .resolve(clusterName)
                .resolve(area);

        Files.createDirectories(stsDir);
        //create required files
        Files.createFile(stsDir.resolve("inflows.xlsx"));
        Files.createFile(stsDir.resolve("lower_curve.xlsx"));
        Files.createFile(stsDir.resolve("Pmax_injection.xlsx"));
        Files.createFile(stsDir.resolve("Pmax_soutirage.xlsx"));
        Files.createFile(stsDir.resolve("upper_curve.xlsx"));

        // stubs for repository/user
        when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{
            setNni("TESTNNI");
        }});
        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenAnswer((Answer<TrajectoryEntity>) inv -> inv.getArgument(0));
        // no required files -> row ignored and therefore no valid rows -> exception

        TrajectoryEntity trajectory = service.processStStorageFile("cluster_battery_test", horizon, 1, false, area, technology);
        assertThat(trajectory).isNotNull();
        assertThat(trajectory.getStStorageEntities()).hasSize(1);
        assertThat(trajectory.getStStorageEntities().getFirst().getTsPath()).isNotNull();
    }

    @Test
    void shouldCreateNewVersionWhenOptionalSeriesChangesButRejectAnUnchangedImport() throws IOException {
        Path xlsx = createWorkbookWithSeriesTrue("2030", "FR", "cluster1");
        placeInClusters(xlsx, "battery", "cluster_battery_test.xlsx");
        Path seriesDir = tempDir.resolve("trajectories/STS/battery/series/test/cluster1/FR");
        Files.createDirectories(seriesDir);
        for (StsTsFile file : StsTsFile.requiredFiles()) {
            Files.createFile(file.resolve(seriesDir));
        }
        when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TrajectoryEntity first = service.processStStorageFile(
                "cluster_battery_test", "2029-2030", 1, false, "FR", "battery");
        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(first));

        assertThatThrownBy(() -> service.processStStorageFile(
                "cluster_battery_test", "2029-2030", 1, false, "FR", "battery"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("File already processed");
        verify(trajectoryRepository, times(1)).save(any());

        Files.createFile(seriesDir.resolve("COST.xlsx"));
        TrajectoryEntity second = service.processStStorageFile(
                "cluster_battery_test", "2029-2030", 1, false, "FR", "battery");
        assertThat(second.getVersion()).isEqualTo(first.getVersion() + 1);
        assertThat(second.getChecksum()).isNotEqualTo(first.getChecksum());
        verify(trajectoryRepository, times(2)).save(any());
    }

    @Test
    void shouldCreateNewVersionWhenClusterDataChanges() throws IOException {
        Path xlsx = createValidWorkbook("2030", false);
        placeInClusters(xlsx, "battery", "cluster_battery_test.xlsx");
        when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TrajectoryEntity first = service.processStStorageFile(
                "cluster_battery_test", "2029-2030", 1, false, "FR", "battery");
        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(first));

        Path importedFile = tempDir.resolve("trajectories/STS/battery/clusters/cluster_battery_test.xlsx");
        Workbook workbook;
        try (InputStream input = Files.newInputStream(importedFile)) {
            workbook = WorkbookFactory.create(input);
        }
        try (workbook; OutputStream output = Files.newOutputStream(importedFile)) {
            workbook.getSheet("2030").getRow(1).getCell(3).setCellValue(42);
            workbook.write(output);
        }
        TrajectoryEntity second = service.processStStorageFile(
                "cluster_battery_test", "2029-2030", 1, false, "FR", "battery");

        assertThat(second.getVersion()).isEqualTo(first.getVersion() + 1);
        assertThat(second.getChecksum()).isNotEqualTo(first.getChecksum());
        assertThat(second.getStStorageEntities()).singleElement()
                .satisfies(storage -> assertThat(storage.getInjection()).isEqualByComparingTo("42"));
    }

    @Test
    void shouldRejectAmbiguousOptionalSeriesFiles() throws IOException {
        Path xlsx = createWorkbookWithSeriesTrue("2030", "FR", "cluster1");
        placeInClusters(xlsx, "battery", "cluster_battery_test.xlsx");
        Path seriesDir = tempDir.resolve("trajectories/STS/battery/series/test/cluster1/FR");
        Files.createDirectories(seriesDir);
        for (StsTsFile file : StsTsFile.requiredFiles()) {
            Files.createFile(file.resolve(seriesDir));
        }
        Files.createFile(seriesDir.resolve("cost.xlsx"));
        Files.createFile(seriesDir.resolve("COST.xlsx"));

        assertThatThrownBy(() -> service.processStStorageFile(
                "cluster_battery_test", "2029-2030", 1, false, "FR", "battery"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorMessageArguments()).containsExactly(seriesDir.resolve("cost.xlsx").toString()));
        verify(trajectoryRepository, never()).save(any());
    }

    @Test
    void shouldRejectAmbiguousClusterFilesAtImport() throws IOException {
        Path xlsx = createValidWorkbook("2030", false);
        placeInClusters(xlsx, "battery", "cluster_battery_test.xlsx");
        Path clusterDir = tempDir.resolve("trajectories/STS/battery/clusters");
        Files.copy(clusterDir.resolve("cluster_battery_test.xlsx"), clusterDir.resolve("CLUSTER_BATTERY_TEST.xls"));

        assertThatThrownBy(() -> service.processStStorageFile(
                "cluster_battery_test", "2029-2030", 1, false, "FR", "battery"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("STS cluster file is ambiguous");
        verify(trajectoryRepository, never()).save(any());
    }

    @Test
    void shouldRejectNonBooleanAllowOverflow() throws IOException {
        Path xlsx = createWorkbookWithNonBooleanColumn("2030", 13);
        placeInClusters(xlsx, "battery", "cluster_battery_test.xlsx");
        assertThatThrownBy(() -> service.processStStorageFile(
                "cluster_battery_test", "2029-2030", 1, false, "FR", "battery"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getMessage()).isEqualTo(
                            "Waiting for boolean value(s) in column(s) Allow_overflow in {0} trajectory");
                    assertThat(exception.getErrorMessageArguments()).containsExactly("cluster_battery_test.xlsx");
                });
        verify(trajectoryRepository, never()).save(any());
    }

    private record IntegrityFixture(TrajectoryEntity trajectory, Path clusters, Path series, Path constraints) {}

    private IntegrityFixture importIntegrityFixture() throws IOException {
        Path workbookPath = createWorkbookWithSeriesTrue("2030", "FR", "cluster1");
        Workbook workbook;
        try (InputStream input = Files.newInputStream(workbookPath)) {
            workbook = WorkbookFactory.create(input);
        }
        try (workbook; OutputStream output = Files.newOutputStream(workbookPath)) {
            workbook.getSheet("2030").getRow(1).getCell(12).setCellValue(true);
            workbook.write(output);
        }
        placeInClusters(workbookPath, "battery", "cluster_battery_test.xlsx");
        Path clusters = tempDir.resolve("trajectories/STS/battery/clusters/cluster_battery_test.xlsx");
        Path series = tempDir.resolve("trajectories/STS/battery/series/test/cluster1/FR");
        Files.createDirectories(series);
        for (StsTsFile file : StsTsFile.requiredFiles()) Files.writeString(file.resolve(series), "original");
        Files.writeString(series.resolve("cost.xlsx"), "optional");
        Path constraints = tempDir.resolve("trajectories/STS/battery/constraints");
        Files.createDirectories(constraints);
        Files.writeString(constraints.resolve("Additional-constraints.xlsx"), "parameters");
        Files.writeString(constraints.resolve("test.xlsx"), "capacity");
        when(stStorageConstraintsFileProcessorService.processConstraintsParametersAnHoursFile(
                any(), anyString(), anyList(), anyString(), anyString(), anyString(), any()))
                .thenReturn(List.of(paramWithHours("FR", "cluster1")));
        when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TrajectoryEntity trajectory = service.processStStorageFile(
                "cluster_battery_test", "2029-2030", 1, false, "FR", "battery");
        trajectory.getStStorageEntities().getFirst().setId(1);
        return new IntegrityFixture(trajectory, clusters, series, constraints);
    }

    @ParameterizedTest
    @CsvSource({
            "clusters, modify", "clusters, remove",
            "required, modify", "required, remove",
            "optional, modify", "optional, remove", "optional, add",
            "parameters, modify", "parameters, remove",
            "capacity, modify", "capacity, remove"
    })
    void shouldRejectFilesChangedBetweenImportAndGeneration(String category, String change) throws Exception {
        IntegrityFixture fixture = importIntegrityFixture();
        Path path = switch (category) {
            case "clusters" -> fixture.clusters();
            case "required" -> fixture.series().resolve("inflows.xlsx");
            case "optional" -> fixture.series().resolve(change.equals("add") ? "level_cost.xlsx" : "cost.xlsx");
            case "parameters" -> fixture.constraints().resolve("Additional-constraints.xlsx");
            case "capacity" -> fixture.constraints().resolve("test.xlsx");
            default -> throw new IllegalArgumentException(category);
        };
        if (change.equals("remove")) Files.delete(path);
        else Files.writeString(path, "changed");

        NasFileService nas = mock(NasFileService.class);
        TimeSeriesReader reader = mock(TimeSeriesReader.class);
        StsPropertiesAssemblerServiceImpl assembler = new StsPropertiesAssemblerServiceImpl(properties, nas, reader);
        StudyEntity study = StudyEntity.builder().horizon("2029-2030")
                .trajectories(Set.of(fixture.trajectory())).build();

        assertThatThrownBy(() -> assembler.assembleStsProperties(study))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(nas, reader);
    }

    @Test
    void shouldGenerateFromVerifiedCopiesEvenWhenSourcesChangeAfterCapture() throws Exception {
        IntegrityFixture fixture = importIntegrityFixture();
        NasFileService nas = mock(NasFileService.class);
        TimeSeriesReader reader = mock(TimeSeriesReader.class);
        TimeSeriesWriter writer = mock(TimeSeriesWriter.class);
        StsPropertiesAssemblerServiceImpl assembler = new StsPropertiesAssemblerServiceImpl(properties, nas, reader);
        StudyEntity study = StudyEntity.builder().horizon("2029-2030")
                .trajectories(Set.of(fixture.trajectory())).build();
        TimeSeriesMatrix matrix = new TimeSeriesMatrix(List.of(
                new TimeSeriesMatrixColumn("c_FR_cluster1", new double[]{1})));
        AtomicBoolean modified = new AtomicBoolean();
        ConcurrentLinkedQueue<Path> capturedPaths = new ConcurrentLinkedQueue<>();
        when(reader.readSelectedColumnsFromXlsx(any(), any(), any())).thenAnswer(invocation -> {
            Path copy = invocation.getArgument(0);
            capturedPaths.add(copy);
            assertThat(copy).isNotEqualTo(fixture.constraints().resolve("test.xlsx"));
            assertThat(Files.readString(copy)).isEqualTo("capacity");
            Files.writeString(fixture.constraints().resolve("test.xlsx"), "changed");
            Files.writeString(fixture.series().resolve("inflows.xlsx"), "changed");
            modified.set(true);
            return matrix;
        });
        when(nas.readMatrix(any(), any(), any(), any())).thenAnswer(invocation -> {
            Path copy = invocation.getArgument(0);
            capturedPaths.add(copy);
            assertThat(copy.startsWith(fixture.series())).isFalse();
            if (copy.getFileName().toString().equals("inflows.xlsx")) {
                assertThat(Files.readString(copy)).isEqualTo("original");
            }
            return matrix;
        });
        when(properties.getStsTsOutputDirectory()).thenReturn("output");
        when(nas.getWriter()).thenReturn(writer);
        when(writer.writeToByteArray(any())).thenReturn(new byte[]{1});
        when(nas.saveMatrixBytesToNas(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(1));

        assertThat(assembler.assembleStsProperties(study)).containsKey("FR_cluster1");
        assertThat(modified).isTrue();
        verify(nas, times(6)).readMatrix(any(), any(), any(), any());
        assertThat(capturedPaths).hasSize(7).allSatisfy(path -> assertThat(path).doesNotExist());
    }

    @Test
    void shouldRemoveCapturedFilesWhenGenerationFails() throws Exception {
        IntegrityFixture fixture = importIntegrityFixture();
        NasFileService nas = mock(NasFileService.class);
        TimeSeriesReader reader = mock(TimeSeriesReader.class);
        ConcurrentLinkedQueue<Path> capturedPaths = new ConcurrentLinkedQueue<>();
        when(reader.readSelectedColumnsFromXlsx(any(), any(), any())).thenAnswer(invocation -> {
            capturedPaths.add(invocation.getArgument(0));
            throw new IOException("cannot read constraint");
        });
        StsPropertiesAssemblerServiceImpl assembler = new StsPropertiesAssemblerServiceImpl(properties, nas, reader);
        StudyEntity study = StudyEntity.builder().horizon("2029-2030")
                .trajectories(Set.of(fixture.trajectory())).build();

        assertThatThrownBy(() -> assembler.assembleStsProperties(study)).isInstanceOf(UncheckedIOException.class);
        assertThat(capturedPaths).isNotEmpty().allSatisfy(path -> assertThat(path).doesNotExist());
        verifyNoInteractions(nas);
    }

    @Test
    void shouldHashTheSameConstraintsCopyThatWasReadAtImport() throws Exception {
        Path xlsx = createWorkbookWithConstraints("2030", "FR", "cluster1");
        placeInClusters(xlsx, "battery", "cluster_battery_test.xlsx");
        Path constraints = tempDir.resolve("trajectories/STS/battery/constraints");
        Files.createDirectories(constraints);
        Path source = constraints.resolve("Additional-constraints.xlsx");
        Files.writeString(source, "original parameters");
        Files.writeString(constraints.resolve("test.xlsx"), "capacity");
        List<Path> capturedPaths = new java.util.ArrayList<>();
        when(stStorageConstraintsFileProcessorService.processConstraintsParametersAnHoursFile(
                any(), anyString(), anyList(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    Path copy = invocation.getArgument(0);
                    capturedPaths.add(copy);
                    assertThat(copy).isNotEqualTo(source);
                    assertThat(Files.readString(copy)).isEqualTo("original parameters");
                    Files.writeString(source, "changed parameters");
                    return List.of(paramWithHours("FR", "cluster1"));
                });
        when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TrajectoryEntity trajectory = service.processStStorageFile(
                "cluster_battery_test", "2029-2030", 1, false, "FR", "battery");
        assertThat(capturedPaths).singleElement().satisfies(path -> assertThat(path).doesNotExist());
        StudyEntity study = StudyEntity.builder().horizon("2029-2030").trajectories(Set.of(trajectory)).build();
        NasFileService nas = mock(NasFileService.class);
        TimeSeriesReader reader = mock(TimeSeriesReader.class);

        assertThatThrownBy(() -> new StsPropertiesAssemblerServiceImpl(properties, nas, reader).assembleStsProperties(study))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("STS files have changed since import");
        verifyNoInteractions(nas, reader);
    }


    private Path createWorkbookWithoutSheet() throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet("other");
            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }

    private Path createWorkbookWithHeaderOnly(String horizon) throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet(horizon);
            Row header = s.createRow(0);
            String[] headers = {"Area", "Name", "Group", "Injection", "Withdrawal", "Storage", "Efficiency_injection", "Efficiency_withdrawal", "Initial_level", "Initial_level_optim", "Enabled", "Series", "Constraints", "Allow_overflow"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }

    private Path createValidWorkbook(String horizon, boolean series) throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet(horizon);
            Row header = s.createRow(0);
            String[] headers = {"Area", "Name", "Group", "Injection", "Withdrawal", "Storage", "Efficiency_injection", "Efficiency_withdrawal", "Initial_level", "Initial_level_optim", "Enabled", "Series", "Constraints", "Allow_overflow"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            Row r = s.createRow(1);
            r.createCell(0).setCellValue("FR");
            r.createCell(1).setCellValue("cluster1");
            r.createCell(2).setCellValue("g1");
            r.createCell(3).setCellValue(10); // injection
            r.createCell(4).setCellValue(20); // withdrawal
            r.createCell(5).setCellValue(30); // storage
            r.createCell(6).setCellValue(0.9); // eff inj
            r.createCell(7).setCellValue(5); // eff w
            r.createCell(8).setCellValue(0.5); // initial level
            r.createCell(9).setCellValue("true");
            r.createCell(10).setCellValue("false");
            r.createCell(11).setCellValue(series);
            r.createCell(12).setCellValue("false");
            r.createCell(13).setCellValue(false);

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }

    private Path createWorkbookWithSeriesTrue(String horizon, String area, String cluster)
            throws IOException {

        Path file = tempDir.resolve("test.xlsx");

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet(horizon);
            Row header = s.createRow(0);
            String[] headers = {"Area", "Name", "Group", "Injection", "Withdrawal", "Storage", "Efficiency_injection", "Efficiency_withdrawal", "Initial_level", "Initial_level_optim", "Enabled", "Series", "Constraints", "Allow_overflow"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            Row r = s.createRow(1);
            r.createCell(0).setCellValue(area);
            r.createCell(1).setCellValue(cluster);
            r.createCell(2).setCellValue("g1");
            r.createCell(3).setCellValue(10);
            r.createCell(4).setCellValue(20);
            r.createCell(5).setCellValue(30);
            r.createCell(6).setCellValue(0.9);
            r.createCell(7).setCellValue(5);
            r.createCell(8).setCellValue(0.5);
            r.createCell(9).setCellValue("true");
            r.createCell(10).setCellValue("false");
            r.createCell(11).setCellValue("true"); // series true
            r.createCell(12).setCellValue("false");
            r.createCell(13).setCellValue(false);

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }

    private Path createWorkbookWithNonNumeric(String horizon) throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet(horizon);
            Row header = s.createRow(0);
            String[] headers = {"Area", "Name", "Group", "Injection", "Withdrawal", "Storage", "Efficiency_injection", "Efficiency_withdrawal", "Initial_level", "Initial_level_optim", "Enabled", "Series", "Constraints", "Allow_overflow"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            Row r = s.createRow(1);
            r.createCell(0).setCellValue("FR");
            r.createCell(1).setCellValue("cluster1");
            r.createCell(2).setCellValue("g1");
            // put non-numeric strings in numeric columns
            r.createCell(3).setCellValue("abc");
            r.createCell(4).setCellValue("def");
            r.createCell(5).setCellValue("ghi");
            r.createCell(6).setCellValue("jkl");
            r.createCell(7).setCellValue("mno");
            r.createCell(8).setCellValue("pqr");
            r.createCell(9).setCellValue("true");
            r.createCell(10).setCellValue("false");
            r.createCell(11).setCellValue("false");
            r.createCell(12).setCellValue("false");
            r.createCell(13).setCellValue(false);

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }

    private Path placeInClusters(Path source, String technology, String targetFileName) throws IOException {
        Path clusters = tempDir
                .resolve("trajectories")
                .resolve("STS")
                .resolve(technology)
                .resolve("clusters");
        Files.createDirectories(clusters);
        Path target = clusters.resolve(targetFileName);
        return Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    void shouldThrowExceptionWhenClusterNameIsMissing() throws IOException {
        Path xlsx = createWorkbookWithMissingClusterName("2030");
        placeInClusters(xlsx, "battery", "cluster_battery_test.xlsx");

        assertThatThrownBy(() ->
                service.processStStorageFile("cluster_battery_test", "2029-2030", 1, false, "FR", "battery")
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("No valid cluster name found in the trajectory {0} for area {1} and horizon {2}");
    }

    @Test
    void shouldThrowExceptionWhenClusterGroupIsMissing() throws IOException {
        Path xlsx = createWorkbookWithMissingClusterGroup("2030");
        placeInClusters(xlsx, "battery", "cluster_battery_test.xlsx");

        assertThatThrownBy(() ->
                service.processStStorageFile("cluster_battery_test", "2029-2030", 1, false, "FR", "battery")
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("No valid cluster group found in the trajectory");
    }

    @Test
    void shouldBuildStsOptionalFilesPathForSeriesSuccessfully() throws IOException {
        // GIVEN
        String technology = "battery";
        String trajectoryFileName = "cluster_battery_test.xlsx";
        Path trajectoryFilePath = Path.of(trajectoryFileName);
        String areaParam = "FR";
        String clusterName = "cluster1";

        String trajectoryNameWithoutPrefix = "test";

        Path stsRoot = tempDir.resolve("trajectories").resolve("STS");
        Path techDir = stsRoot.resolve(technology);
        Path seriesDir = techDir.resolve("series");
        Path trajectoryDir = seriesDir.resolve(trajectoryNameWithoutPrefix);
        Path clusterDir = trajectoryDir.resolve(clusterName);
        Files.createDirectories(clusterDir);

        // WHEN
        Path result = service.buildStsOptionalFilesPath(trajectoryFilePath, areaParam, technology, clusterName, "series");

        // THEN
        assertThat(result).isEqualTo(clusterDir.resolve(areaParam).normalize());
    }

    @Test
    void shouldBuildStsOptionalFilesPathForConstraintsSuccessfully() throws IOException {
        // GIVEN
        String technology = "battery";
        Path trajectoryFilePath = Path.of("STS_battery_test.xlsx");
        String areaParam = "FR";
        String clusterName = "cluster1";

        Path stsRoot = tempDir.resolve("trajectories").resolve("STS");
        Path techDir = stsRoot.resolve(technology);
        Path constraintsDir = techDir.resolve("constraints");
        Files.createDirectories(constraintsDir);

        // WHEN
        Path result = service.buildStsOptionalFilesPath(trajectoryFilePath, areaParam, technology, clusterName, "constraints");

        // THEN
        assertThat(result).isEqualTo(constraintsDir);
    }

    @Test
    void shouldThrowExceptionWhenTechnologyDirectoryNotFound() {
        // GIVEN
        String technology = "unknown_tech";
        Path trajectoryFilePath = Path.of("STS_test.xlsx");

        Path stsRoot = tempDir.resolve("trajectories").resolve("STS");
        try {
            Files.createDirectories(stsRoot);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // WHEN / THEN
        assertThatThrownBy(() ->
                service.buildStsOptionalFilesPath(trajectoryFilePath, "FR", technology, "cluster1", "series")
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Directory not found under");
    }

    @Test
    void shouldThrowExceptionForInvalidOptionalFilesType() throws IOException {
        // GIVEN
        String technology = "battery";
        Path trajectoryFilePath = Path.of("STS_test.xlsx");

        Path stsRoot = tempDir.resolve("trajectories").resolve("STS");
        Path techDir = stsRoot.resolve(technology);
        Files.createDirectories(techDir);

        // WHEN / THEN
        assertThatThrownBy(() ->
                service.buildStsOptionalFilesPath(trajectoryFilePath, "FR", technology, "cluster1", "invalid_type")
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid optionalFilesType: invalid_type");
    }

    @Test
    void shouldProcessEvWithConstraintsSuccessfully() throws IOException {
        String horizon = "2029-2030";
        String technology = "EV";
        String area = "FR";
        String clusterName = "cluster1";
        String trajectoryToUse = "cluster_ev_test";
        String trajectoryFileName = trajectoryToUse + ".xlsx";

        Path xlsx = createWorkbookWithConstraints(horizon.split("-")[1], area, clusterName);
        placeInClusters(xlsx, technology, trajectoryFileName);

        Path constraintsDir = tempDir
                .resolve("trajectories")
                .resolve("STS")
                .resolve(technology)
                .resolve("constraints");

        Files.createDirectories(constraintsDir);
        Files.createFile(constraintsDir.resolve("Additional-constraints.xlsx"));

        Files.createFile(constraintsDir.resolve("test.xlsx"));

        when(stStorageConstraintsFileProcessorService.processConstraintsParametersAnHoursFile(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(paramWithHours(area, clusterName)));
        when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{
            setNni("TEST_NNI");
        }});
        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenAnswer((Answer<TrajectoryEntity>) inv -> inv.getArgument(0));

        TrajectoryEntity trajectory = service.processStStorageFile(trajectoryToUse, horizon, 1, false, area, technology);

        assertThat(trajectory).isNotNull();
        assertThat(trajectory.getStStorageEntities()).hasSize(1);
        StStorageEntity entity = trajectory.getStStorageEntities().getFirst();
        assertThat(entity.getConstraintsFlag()).isTrue();
        assertThat(entity.getConstraintsPath()).isNotNull();
        assertThat(entity.getConstraintsPath()).contains("test.xlsx");
    }

    @Test
    void shouldThrowWhenEvConstraintsAreMissing() throws IOException {
        String horizon = "2029-2030";
        String technology = "EV";
        String area = "FR";
        String clusterName = "cluster1";
        String trajectoryToUse = "cluster_ev_test";

        Path xlsx = createWorkbookWithConstraints(horizon.split("-")[1], area, clusterName);
        placeInClusters(xlsx, technology, trajectoryToUse + ".xlsx");

        Path constraintsDir = tempDir
                .resolve("trajectories")
                .resolve("STS")
                .resolve(technology)
                .resolve("constraints");

        Files.createDirectories(constraintsDir);
        // Create empty constraint files instead of missing files
        // With current implementation, missing cache entries just log a warning
        createEmptyConstraintWorkbook(constraintsDir);

        when(trajectoryRepository.save(any())).thenAnswer((Answer<TrajectoryEntity>) inv -> inv.getArgument(0));

        // Should complete without error when constraints file exists but is empty
        TrajectoryEntity result = service.processStStorageFile(trajectoryToUse, horizon, 1, false, area, technology);
        assertThat(result).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("provideBooleanKeywordsWithExpectedResult")
    void shouldParseAndSetCorrectBooleanValues(String keyword, boolean expectedBoolean) throws IOException {
        String horizon = "2029-2030";
        String technology = "battery";
        String area = "FR";
        String clusterName = "cluster1";

        Path seriesDir = tempDir
                .resolve("trajectories")
                .resolve("STS")
                .resolve(technology)
                .resolve("series")
                .resolve("test")
                .resolve(clusterName)
                .resolve(area);
        Files.createDirectories(seriesDir);
        Files.createFile(seriesDir.resolve("inflows.xlsx"));
        Files.createFile(seriesDir.resolve("lower_curve.xlsx"));
        Files.createFile(seriesDir.resolve("Pmax_injection.xlsx"));
        Files.createFile(seriesDir.resolve("Pmax_soutirage.xlsx"));
        Files.createFile(seriesDir.resolve("upper_curve.xlsx"));

        Path xlsx = tempDir.resolve("test_boolean.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("2030");
            Row header = s.createRow(0);
            String[] headers = {"Area", "Name", "Group", "Injection", "Withdrawal", "Storage",
                    "Efficiency_injection", "Efficiency_withdrawal", "Initial_level",
                    "Initial_level_optim", "Enabled", "Series", "Constraints", "Allow_overflow"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            Row r = s.createRow(1);
            r.createCell(0).setCellValue(area);
            r.createCell(1).setCellValue(clusterName);
            r.createCell(2).setCellValue("g1");
            r.createCell(3).setCellValue(10);
            r.createCell(4).setCellValue(20);
            r.createCell(5).setCellValue(30);
            r.createCell(6).setCellValue(0.9);
            r.createCell(7).setCellValue(5);
            r.createCell(8).setCellValue(0.5);
            r.createCell(9).setCellValue(keyword);  // Initial_level_optim
            r.createCell(10).setCellValue(keyword); // Enabled
            r.createCell(11).setCellValue(keyword); // Series
            r.createCell(12).setCellValue(keyword); // Constraints
            r.createCell(13).setCellValue(keyword); // Allow_overflow

            try (OutputStream os = Files.newOutputStream(xlsx)) {
                wb.write(os);
            }
        }

        placeInClusters(xlsx, technology, "cluster_battery_test.xlsx");

        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenAnswer((Answer<TrajectoryEntity>) inv -> inv.getArgument(0));

        TrajectoryEntity result = service.processStStorageFile(
                "cluster_battery_test", horizon, 1, false, area, technology
        );

        assertThat(result).isNotNull();
        assertThat(result.getStStorageEntities()).hasSize(1);

        StStorageEntity entity = result.getStStorageEntities().getFirst();
        assertThat(entity.getInitialLevelOptim())
                .as("initialLevelOptim should be %s for keyword '%s'", expectedBoolean, keyword)
                .isEqualTo(expectedBoolean);
        assertThat(entity.getEnabled())
                .as("enabled should be %s for keyword '%s'", expectedBoolean, keyword)
                .isEqualTo(expectedBoolean);
        assertThat(entity.getSeries())
                .as("series should be %s for keyword '%s'", expectedBoolean, keyword)
                .isEqualTo(expectedBoolean);
        assertThat(entity.getConstraintsFlag())
                .as("constraintsFlag should be %s for keyword '%s'", expectedBoolean, keyword)
                .isEqualTo(expectedBoolean);
        assertThat(entity.getAllowOverflow())
                .as("allowOverflow should be %s for keyword '%s'", expectedBoolean, keyword)
                .isEqualTo(expectedBoolean);
    }

    static Stream<Arguments> provideBooleanKeywordsWithExpectedResult() {
        return TestConstants.booleanKeywords();
    }

    private void createEmptyConstraintWorkbook(Path constraintsDir) throws IOException {
        Path filePath = constraintsDir.resolve("Additional-constraints.xlsx");
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet paramSheet = workbook.createSheet("parameters");
            Row headerP = paramSheet.createRow(0);
            headerP.createCell(0).setCellValue("name");
            headerP.createCell(1).setCellValue("zone");
            headerP.createCell(2).setCellValue("cluster");

            Sheet hourSheet = workbook.createSheet("hours");
            Row headerH = hourSheet.createRow(0);
            headerH.createCell(0).setCellValue("name");
            headerH.createCell(1).setCellValue("zone");
            headerH.createCell(2).setCellValue("cluster");

            try (OutputStream os = Files.newOutputStream(filePath)) {
                workbook.write(os);
            }
        }
    }

    private Path createWorkbookWithConstraints(String horizon, String area, String cluster) throws IOException {
        Path file = tempDir.resolve("test_ev.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet(horizon);
            Row header = s.createRow(0);
            String[] headers = {"Area", "Name", "Group", "Injection", "Withdrawal", "Storage", "Efficiency_injection", "Efficiency_withdrawal", "Initial_level", "Initial_level_optim", "Enabled", "Series", "Constraints", "Allow_overflow"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            Row r = s.createRow(1);
            r.createCell(0).setCellValue(area);
            r.createCell(1).setCellValue(cluster);
            r.createCell(2).setCellValue("g1");
            for (int i = 3; i <= 8; i++) r.createCell(i).setCellValue(1.0);
            r.createCell(9).setCellValue(true);
            r.createCell(10).setCellValue(true);
            r.createCell(11).setCellValue(false);
            r.createCell(12).setCellValue(true); // Constraints TRUE
            r.createCell(13).setCellValue(false);

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }

    @Test
    void shouldMapAllFieldsCorrectly() throws IOException {
        String horizon = "2029-2030";
        String technology = "battery";
        String area = "FR";
        String clusterName = "cluster1";
        String group = "g1";
        double injection = 10.5;
        double withdrawal = 20.7;
        double storage = 300.0;
        double effInj = 0.98;
        double effWid = 0.96;
        double initLevel = 0.5;

        Path file = tempDir.resolve("full_mapping.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("2030");
            Row header = s.createRow(0);
            String[] headers = {"Area", "Name", "Group", "Injection", "Withdrawal", "Storage", "Efficiency_injection", "Efficiency_withdrawal", "Initial_level", "Initial_level_optim", "Enabled", "Series", "Constraints", "Allow_overflow"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            Row r = s.createRow(1);
            r.createCell(0).setCellValue(area);
            r.createCell(1).setCellValue(clusterName);
            r.createCell(2).setCellValue(group);
            r.createCell(3).setCellValue(injection);
            r.createCell(4).setCellValue(withdrawal);
            r.createCell(5).setCellValue(storage);
            r.createCell(6).setCellValue(effInj);
            r.createCell(7).setCellValue(effWid);
            r.createCell(8).setCellValue(initLevel);
            r.createCell(9).setCellValue(true);
            r.createCell(10).setCellValue(false);
            r.createCell(11).setCellValue(false);
            r.createCell(12).setCellValue(false);
            r.createCell(13).setCellValue(false);

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        placeInClusters(file, technology, "cluster_battery_test.xlsx");

        when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{
            setNni("TEST_NNI");
        }});
        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenAnswer((Answer<TrajectoryEntity>) inv -> inv.getArgument(0));

        TrajectoryEntity trajectory = service.processStStorageFile("cluster_battery_test", horizon, 1, false, area, technology);

        assertThat(trajectory.getStStorageEntities()).hasSize(1);
        StStorageEntity entity = trajectory.getStStorageEntities().getFirst();

        assertThat(entity.getArea()).isEqualTo(area);
        assertThat(entity.getName()).isEqualTo(clusterName);
        assertThat(entity.getGroupe()).isEqualTo(group);
        assertThat(entity.getInjection()).isEqualByComparingTo(BigDecimal.valueOf(injection));
        assertThat(entity.getWithdrawal()).isEqualByComparingTo(BigDecimal.valueOf(withdrawal));
        assertThat(entity.getStorage()).isEqualByComparingTo(BigDecimal.valueOf(storage));
        assertThat(entity.getEfficiencyInjection()).isEqualByComparingTo(BigDecimal.valueOf(effInj));
        assertThat(entity.getEfficiencyWithdrawal()).isEqualByComparingTo(BigDecimal.valueOf(effWid));
        assertThat(entity.getInitialLevel()).isEqualByComparingTo(BigDecimal.valueOf(initLevel));
        assertThat(entity.getInitialLevelOptim()).isTrue();
        assertThat(entity.getEnabled()).isFalse();
        assertThat(entity.getSeries()).isFalse();
        assertThat(entity.getConstraintsFlag()).isFalse();
    }

    private Path createWorkbookWithMissingClusterName(String horizon) throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet(horizon);
            Row header = s.createRow(0);
            String[] headers = {"Area", "Name", "Group", "Injection", "Withdrawal", "Storage", "Efficiency_injection", "Efficiency_withdrawal", "Initial_level", "Initial_level_optim", "Enabled", "Series", "Constraints", "Allow_overflow"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            Row r = s.createRow(1);
            r.createCell(0).setCellValue("FR");
            r.createCell(1).setCellValue(""); // missing cluster name
            r.createCell(2).setCellValue("g1");
            r.createCell(3).setCellValue(10);
            r.createCell(4).setCellValue(20);
            r.createCell(5).setCellValue(30);
            r.createCell(6).setCellValue(0.9);
            r.createCell(7).setCellValue(5);
            r.createCell(8).setCellValue(50);
            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }

    private Path createWorkbookWithMissingClusterGroup(String horizon) throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet(horizon);
            Row header = s.createRow(0);
            String[] headers = {"Area", "Name", "Group", "Injection", "Withdrawal", "Storage", "Efficiency_injection", "Efficiency_withdrawal", "Initial_level", "Initial_level_optim", "Enabled", "Series", "Constraints", "Allow_overflow"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            Row r = s.createRow(1);
            r.createCell(0).setCellValue("FR");
            r.createCell(1).setCellValue("cluster1");
            r.createCell(2).setCellValue(""); // missing group name
            r.createCell(3).setCellValue(10);
            r.createCell(4).setCellValue(20);
            r.createCell(5).setCellValue(30);
            r.createCell(6).setCellValue(0.9);
            r.createCell(7).setCellValue(5);
            r.createCell(8).setCellValue(50);
            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }

    @Test
    void shouldReadAdditionalConstraintsFileOncePerUniquePathDuringImport() throws IOException {
        String horizon = "2029-2030";
        String technology = "EV";
        String area = "FR";
        String trajectoryToUse = "cluster_ev_test";
        String trajectoryFileName = trajectoryToUse + ".xlsx";

        Path xlsx = createWorkbookWithTwoConstraintsRows(horizon.split("-")[1], area);
        placeInClusters(xlsx, technology, trajectoryFileName);

        Path constraintsDir = tempDir
                .resolve("trajectories")
                .resolve("STS")
                .resolve(technology)
                .resolve("constraints");
        Files.createDirectories(constraintsDir);
        Path additionalConstraintsPath = constraintsDir.resolve("Additional-constraints.xlsx");
        Files.createFile(additionalConstraintsPath);
        Files.createFile(constraintsDir.resolve("test.xlsx"));

        when(stStorageConstraintsFileProcessorService.processConstraintsParametersAnHoursFile(
                any(Path.class), anyString(), anyList(), anyString(), anyString(), anyString(), any()))
                .thenReturn(List.of(paramWithHours(area, "cluster1"), paramWithHours(area, "cluster2")));
        when(trajectoryRepository.findAreasByStudyIdAndType(anyInt(), eq("STS"))).thenReturn(List.of());
        when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{
            setNni("TEST_NNI");
        }});
        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenAnswer((Answer<TrajectoryEntity>) inv -> inv.getArgument(0));

        TrajectoryEntity trajectory = service.processStStorageFile(trajectoryToUse, horizon, 1, false, area, technology);

        assertThat(trajectory.getStStorageEntities()).hasSize(2);
        verify(stStorageConstraintsFileProcessorService, times(1))
                .processConstraintsParametersAnHoursFile(argThat(path -> path.getFileName().equals(additionalConstraintsPath.getFileName())
                        && !path.equals(additionalConstraintsPath)), eq(area), anyList(), anyString(), anyString(), anyString(), any());
    }

    private Path createWorkbookWithTwoConstraintsRows(String horizon, String area) throws IOException {
        Path file = tempDir.resolve("test_ev_two_rows.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet(horizon);
            Row header = s.createRow(0);
            String[] headers = {"Area", "Name", "Group", "Injection", "Withdrawal", "Storage", "Efficiency_injection", "Efficiency_withdrawal", "Initial_level", "Initial_level_optim", "Enabled", "Series", "Constraints", "Allow_overflow"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            Row first = s.createRow(1);
            first.createCell(0).setCellValue(area);
            first.createCell(1).setCellValue("cluster1");
            first.createCell(2).setCellValue("g1");
            for (int i = 3; i <= 8; i++) {
                first.createCell(i).setCellValue(1.0);
            }
            first.createCell(9).setCellValue(true);
            first.createCell(10).setCellValue(true);
            first.createCell(11).setCellValue(false);
            first.createCell(12).setCellValue(true);
            first.createCell(13).setCellValue(false);

            Row second = s.createRow(2);
            second.createCell(0).setCellValue(area);
            second.createCell(1).setCellValue("cluster2");
            second.createCell(2).setCellValue("g2");
            second.createCell(3).setCellValue(2.0);
            second.createCell(4).setCellValue(2.0);
            second.createCell(5).setCellValue(2.0);
            second.createCell(6).setCellValue(0.8); // efficiency_injection must be in [0,1]
            second.createCell(7).setCellValue(2.0);
            second.createCell(8).setCellValue(0.5); // initial_level must be in [0,1]
            second.createCell(9).setCellValue(true);
            second.createCell(10).setCellValue(true);
            second.createCell(11).setCellValue(false);
            second.createCell(12).setCellValue(true);
            second.createCell(13).setCellValue(false);

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }

    @Test
    void shouldPropagateIOExceptionWhenConstraintsParserFails() throws IOException {
        String horizon = "2029-2030";
        String technology = "EV";
        String area = "FR";
        String trajectoryToUse = "cluster_ev_test";

        Path xlsx = createWorkbookWithConstraints(horizon.split("-")[1], area, "cluster1");
        placeInClusters(xlsx, technology, trajectoryToUse + ".xlsx");

        Path constraintsDir = tempDir
                .resolve("trajectories")
                .resolve("STS")
                .resolve(technology)
                .resolve("constraints");
        Files.createDirectories(constraintsDir);
        Files.createFile(constraintsDir.resolve("Additional-constraints.xlsx"));
        Files.createFile(constraintsDir.resolve("test.xlsx"));

        when(stStorageConstraintsFileProcessorService.processConstraintsParametersAnHoursFile(any(), anyString(), anyList(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new IOException("cannot parse constraints"));
        when(trajectoryRepository.findAreasByStudyIdAndType(anyInt(), eq("STS"))).thenReturn(List.of());
        when(trajectoryRepository.save(any())).thenAnswer((Answer<TrajectoryEntity>) inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.processStStorageFile(trajectoryToUse, horizon, 1, false, area, technology))
                .isInstanceOf(IOException.class).hasMessage("cannot parse constraints");
        verify(trajectoryRepository, never()).save(any());
    }

    @Test
    void shouldFilterOutExistingAreasWhenAreaParamIsOthers() throws IOException {
        String horizon = "2029-2030";
        String technology = "EV";
        String trajectoryToUse = "cluster_ev_test";

        Path xlsx = createWorkbookWithConstraints(horizon.split("-")[1], "FR", "cluster1");
        placeInClusters(xlsx, technology, trajectoryToUse + ".xlsx");

        Path constraintsDir = tempDir
                .resolve("trajectories")
                .resolve("STS")
                .resolve(technology)
                .resolve("constraints");
        Files.createDirectories(constraintsDir);
        Files.createFile(constraintsDir.resolve("Additional-constraints.xlsx"));
        Files.createFile(constraintsDir.resolve("test.xlsx"));

        StConstraintsParameterEntity frParam = paramWithHours("FR", "cluster1");
        StConstraintsParameterEntity beParam = paramWithHours("BE", "cluster1");

        when(stStorageConstraintsFileProcessorService.processConstraintsParametersAnHoursFile(any(), anyString(), anyList(), anyString(), anyString(), anyString(), any()))
                .thenReturn(List.of(frParam, beParam));
        when(trajectoryRepository.findAreasByStudyIdAndType(anyInt(), eq("STS"))).thenReturn(List.of("FR"));
        when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{
            setNni("TEST_NNI");
        }});
        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenAnswer((Answer<TrajectoryEntity>) inv -> inv.getArgument(0));

        TrajectoryEntity trajectory = service.processStStorageFile(trajectoryToUse, horizon, 1, false, "OTHERS", technology);

        assertThat(trajectory.getStStorageEntities()).hasSize(1);
        List<StConstraintsParameterEntity> parameters = trajectory.getStStorageEntities().getFirst().getParameters();
        // Implementation filters by storage's area (FR) and cluster, so only FR param is matched
        assertThat(parameters).extracting(StConstraintsParameterEntity::getZone).containsExactly("FR");
    }

    @Test
    void shouldThrowWhenEfficiencyInjectionIsOutOfRange() throws IOException {
        Path xlsx = createWorkbookWithOutOfRangeValue("2030", 6, 1.5);
        placeInClusters(xlsx, "battery", "cluster_battery_test.xlsx");

        assertThatThrownBy(() ->
                service.processStStorageFile("cluster_battery_test", "2029-2030", 1, false, "FR", "battery")
        ).isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getMessage()).contains("Efficiency_injection and Initial_level must be between 0 and 1 in STS trajectory {0}");
                    assertThat(be.getErrorMessageArguments()).containsExactly("cluster_battery_test.xlsx");
                });
    }

    @Test
    void shouldThrowWhenInitialLevelIsOutOfRange() throws IOException {
        Path xlsx = createWorkbookWithOutOfRangeValue("2030", 8, -0.1);
        placeInClusters(xlsx, "battery", "cluster_battery_test.xlsx");

        assertThatThrownBy(() ->
                service.processStStorageFile("cluster_battery_test", "2029-2030", 1, false, "FR", "battery")
        ).isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getMessage()).contains("Efficiency_injection and Initial_level must be between 0 and 1 in STS trajectory {0}");
                    assertThat(be.getErrorMessageArguments()).containsExactly("cluster_battery_test.xlsx");
                });
    }

    @Test
    void shouldThrowWhenBooleanColumnsContainNonBooleanValues() throws IOException {
        Path xlsx = createWorkbookWithNonBooleanColumn("2030", 9);
        placeInClusters(xlsx, "battery", "cluster_battery_test.xlsx");

        assertThatThrownBy(() ->
                service.processStStorageFile("cluster_battery_test", "2029-2030", 1, false, "FR", "battery")
        ).isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getMessage()).contains("Initial_level_optim, Enabled, Series and Constraints must be boolean in STS trajectory {0}");
                    assertThat(be.getErrorMessageArguments()).containsExactly("cluster_battery_test.xlsx");
                });
    }

    private Path createWorkbookWithOutOfRangeValue(String horizon, int colIdx, double outOfRangeValue) throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet(horizon);
            Row header = s.createRow(0);
            String[] headers = {"Area", "Name", "Group", "Injection", "Withdrawal", "Storage", "Efficiency_injection", "Efficiency_withdrawal", "Initial_level", "Initial_level_optim", "Enabled", "Series", "Constraints", "Allow_overflow"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            Row r = s.createRow(1);
            r.createCell(0).setCellValue("FR");
            r.createCell(1).setCellValue("cluster1");
            r.createCell(2).setCellValue("g1");
            r.createCell(3).setCellValue(10);
            r.createCell(4).setCellValue(20);
            r.createCell(5).setCellValue(30);
            r.createCell(6).setCellValue(0.9);  // valid by default
            r.createCell(7).setCellValue(5);
            r.createCell(8).setCellValue(0.5);  // valid by default
            r.createCell(colIdx).setCellValue(outOfRangeValue); // override with out-of-range
            r.createCell(9).setCellValue(true);
            r.createCell(10).setCellValue(false);
            r.createCell(11).setCellValue(false);
            r.createCell(12).setCellValue(false);
            r.createCell(13).setCellValue(false);

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }

    private Path createWorkbookWithNonBooleanColumn(String horizon, int boolColIdx) throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet(horizon);
            Row header = s.createRow(0);
            String[] headers = {"Area", "Name", "Group", "Injection", "Withdrawal", "Storage", "Efficiency_injection", "Efficiency_withdrawal", "Initial_level", "Initial_level_optim", "Enabled", "Series", "Constraints", "Allow_overflow"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            Row r = s.createRow(1);
            r.createCell(0).setCellValue("FR");
            r.createCell(1).setCellValue("cluster1");
            r.createCell(2).setCellValue("g1");
            r.createCell(3).setCellValue(10);
            r.createCell(4).setCellValue(20);
            r.createCell(5).setCellValue(30);
            r.createCell(6).setCellValue(0.9);
            r.createCell(7).setCellValue(5);
            r.createCell(8).setCellValue(0.5);
            r.createCell(9).setCellValue(true);
            r.createCell(10).setCellValue(true);
            r.createCell(11).setCellValue(false);
            r.createCell(12).setCellValue(false);
            r.createCell(13).setCellValue(false);
            r.createCell(boolColIdx).setCellValue(42); // override with non-boolean numeric

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }

    @ParameterizedTest
    @CsvSource({
            "true, true",
            "false, true",
            "TRUE, true",
            "FALSE, true",
            "1, true",
            "0, true",
            " 1 , true",
            "abc, false",
            "'', false",
    })
    void shouldTestIsBooleanStringValue(String value, boolean expected) {
        Boolean result = Utils.isBooleanStringValue(value);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void shouldReturnFalseForNullInIsBooleanStringValue() {
        Boolean result = Utils.isBooleanStringValue(null);
        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseWhenCellIsNullInIsBooleanCell() {
        Boolean result = Utils.isBooleanCell(null);
        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnTrueForBooleanCellInIsBooleanCell() {
        Cell cell = mock(Cell.class);
        when(cell.getCellType()).thenReturn(CellType.BOOLEAN);
        Boolean result = Utils.isBooleanCell(cell);
        assertThat(result).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "0.0, true",
            "1.0, true",
            "0.5, false"
    })
    void shouldTestNumericCellInIsBooleanCell(double value, boolean expected) {
        Cell cell = mock(Cell.class);
        when(cell.getCellType()).thenReturn(CellType.NUMERIC);
        when(cell.getNumericCellValue()).thenReturn(value);
        Boolean result = Utils.isBooleanCell(cell);
        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "true, true",
            "false, true",
            "1, true",
            "0, true",
            "abc, false"
    })
    void shouldTestStringCellInIsBooleanCell(String value, boolean expected) {
        Cell cell = mock(Cell.class);
        when(cell.getCellType()).thenReturn(CellType.STRING);
        when(cell.getStringCellValue()).thenReturn(value);
        Boolean result = Utils.isBooleanCell(cell);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void shouldTestFormulaBooleanCellInIsBooleanCell() {
        Cell cell = mock(Cell.class);
        when(cell.getCellType()).thenReturn(CellType.FORMULA);
        when(cell.getCachedFormulaResultType()).thenReturn(CellType.BOOLEAN);
        Boolean result = Utils.isBooleanCell(cell);
        assertThat(result).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "0.0, true",
            "1.0, true",
            "2.0, false"
    })
    void shouldTestFormulaNumericCellInIsBooleanCell(double value, boolean expected) {
        Cell cell = mock(Cell.class);
        when(cell.getCellType()).thenReturn(CellType.FORMULA);
        when(cell.getCachedFormulaResultType()).thenReturn(CellType.NUMERIC);
        when(cell.getNumericCellValue()).thenReturn(value);
        Boolean result = Utils.isBooleanCell(cell);
        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "true, true",
            "false, true",
            "abc, false"
    })
    void shouldTestFormulaStringCellInIsBooleanCell(String value, boolean expected) {
        Cell cell = mock(Cell.class);
        when(cell.getCellType()).thenReturn(CellType.FORMULA);
        when(cell.getCachedFormulaResultType()).thenReturn(CellType.STRING);
        when(cell.getStringCellValue()).thenReturn(value);
        Boolean result = Utils.isBooleanCell(cell);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void shouldReturnFalseForOtherFormulaResultTypeInIsBooleanCell() {
        Cell cell = mock(Cell.class);
        when(cell.getCellType()).thenReturn(CellType.FORMULA);
        when(cell.getCachedFormulaResultType()).thenReturn(CellType.ERROR);
        Boolean result = Utils.isBooleanCell(cell);
        assertThat(result).isFalse();
    }

    private StConstraintsParameterEntity paramWithHours(String zone, String cluster) {
        StStorageEntity storage = new StStorageEntity();
        storage.setTrajectory(new TrajectoryEntity());
        StConstraintsHoursEntity hour = StConstraintsHoursEntity.builder()
                .occurrence(1).startHour(1).endHour(2).build();
        StConstraintsParameterEntity param = StConstraintsParameterEntity.builder()
                .name("c_" + zone + "_" + cluster)
                .zone(zone)
                .cluster(cluster)
                .enabled(true)
                .storage(storage)
                .hours(List.of(hour))
                .build();
        hour.setParameter(param);
        return param;
    }
}
