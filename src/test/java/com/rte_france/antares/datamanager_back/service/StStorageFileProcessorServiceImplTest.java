package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.StStorageEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.StStorage.StStorageFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StStorageFileProcessorServiceImplTest {

    private AntaressDataManagerProperties properties;
    private TrajectoryServiceImpl trajectoryService;
    private TrajectoryRepository trajectoryRepository;
    private UserService userService;

    private StStorageFileProcessorServiceImpl service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        properties = mock(AntaressDataManagerProperties.class);
        trajectoryService = mock(TrajectoryServiceImpl.class);
        trajectoryRepository = mock(TrajectoryRepository.class);
        userService = mock(UserService.class);

        service = new StStorageFileProcessorServiceImpl(
                properties,
                trajectoryService,
                trajectoryRepository,
                userService
        );

        when(properties.getNasDirectory()).thenReturn(tempDir.toString());
        when(properties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(properties.getStsDirectory()).thenReturn("STS");
    }

    @Test
    void shouldThrowExceptionWhenTrajectoryNameIsInvalid() {
        assertThatThrownBy(() ->
                service.processStStorageFile(
                        "invalid_name",
                        "2020-2030",
                        1,
                        false,
                        "FR",
                        "battery"
                )
        )
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Trajectory name must start with");
    }

    @Test
    void shouldThrowExceptionWhenSheetIsMissing() throws IOException {
        Path xlsx = createWorkbookWithoutSheet("2030");
        placeInClusters(xlsx, "battery", "cluster_battery_test.xlsx");

        assertThatThrownBy(() ->
                service.processStStorageFile(
                        "cluster_battery_test",
                        "2020-2030",
                        1,
                        false,
                        "FR",
                        "battery"
                )
        )
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No sheet found");
    }

    @Test
    void shouldThrowExceptionWhenNoValidRowsFound() throws IOException {
        Path xlsx = createWorkbookWithHeaderOnly("2030");
        placeInClusters(xlsx, "battery", "cluster_battery_test.xlsx");

        assertThatThrownBy(() ->
                service.processStStorageFile(
                        "cluster_battery_test",
                        "2020-2030",
                        1,
                        false,
                        "FR",
                        "battery"
                )
        )
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No ST Storage data found");
    }

    @Test
    void shouldCreateTrajectoryAndEntitiesSuccessfully() throws IOException {
        Path xlsx = createValidWorkbook("2030", false);
        placeInClusters(xlsx, "battery", "cluster_battery_test.xlsx");

        when(userService.getCurrentUserDetails())
                .thenReturn(new UserInfoDto("nni123", null, null, null));

        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyOrderByVersionDesc(
                any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        when(trajectoryRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        TrajectoryEntity trajectory = service.processStStorageFile(
                "cluster_battery_test",
                "2020-2030",
                1,
                false,
                "FR",
                "battery"
        );

        assertThat(trajectory).isNotNull();
        assertThat(trajectory.getStStorageEntities()).hasSize(1);

        StStorageEntity entity = trajectory.getStStorageEntities().get(0);
        assertThat(entity.getArea()).isEqualTo("FR");
        assertThat(entity.getName()).isEqualTo("cluster1");
        assertThat(entity.getInjection()).isEqualByComparingTo(BigDecimal.valueOf(10));
        assertThat(entity.getSeries()).isFalse();
    }

    @Test
    void shouldIncrementVersionWhenExistingTrajectoryFound() throws IOException {
        Path xlsx = createValidWorkbook("2030", false);
        placeInClusters(xlsx, "battery", "cluster_battery_test.xlsx");

        TrajectoryEntity existing = new TrajectoryEntity();
        existing.setVersion(1);

        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyOrderByVersionDesc(
                any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(existing));

        when(trajectoryRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        service.processStStorageFile(
                "cluster_battery_test",
                "2020-2030",
                1,
                false,
                "FR",
                "battery"
        );

        ArgumentCaptor<TrajectoryEntity> captor = ArgumentCaptor.forClass(TrajectoryEntity.class);
        verify(trajectoryRepository).save(captor.capture());

        assertThat(captor.getValue().getVersion()).isEqualTo(1);
    }

    @Test
    void shouldIgnoreRowWhenSeriesIsTrueAndTsFilesAreMissing() throws IOException {
        // given
        String horizon = "2030";
        String technology = "battery";
        String area = "FR";
        String clusterName = "cluster1";

        Path xlsx = createWorkbookWithSeriesTrue(horizon, area, clusterName);
        placeInClusters(xlsx, technology, "cluster_battery_test.xlsx");

        // Create STS directory but WITHOUT required files
        Path stsDir = tempDir
                .resolve("trajectories")
                .resolve("STS")
                .resolve(technology)
                .resolve("series")
                .resolve("test") // filename without prefix/extension
                .resolve(clusterName)
                .resolve(area);

        Files.createDirectories(stsDir);

        // when / then
        assertThatThrownBy(() ->
                service.processStStorageFile(
                        "cluster_battery_test",
                        "2020-2030",
                        1,
                        false,
                        area,
                        technology
                )
        )
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No ST Storage data found");
    }


    @Test
    void shouldIgnoreRowWhenSeriesIsTrueAndStsDirectoryIsMissing() throws IOException {
        // given
        String horizon = "2030";
        String technology = "battery";
        String area = "FR";
        String clusterName = "cluster1";

        Path xlsx = createWorkbookWithSeriesTrue(horizon, area, clusterName);
        placeInClusters(xlsx, technology, "cluster_battery_test.xlsx");

        // when / then
        assertThatThrownBy(() ->
                service.processStStorageFile(
                        "cluster_battery_test",
                        "2020-2030",
                        1,
                        false,
                        area,
                        technology
                )
        )
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No ST Storage data found");
    }
    @Test
    void shouldIgnoreRowWhenSeriesIsTrueAndStsDirectoryIsEmpty() throws IOException {
        // given
        String horizon = "2030";
        String technology = "battery";
        String area = "FR";
        String clusterName = "cluster1";

        Path xlsx = createWorkbookWithSeriesTrue(horizon, area, clusterName);
        placeInClusters(xlsx, technology, "cluster_battery_test.xlsx");

        // Create empty STS directory
        Path stsDir = tempDir
                .resolve("trajectories")
                .resolve("STS")
                .resolve(technology)
                .resolve("series")
                .resolve("test") // filename without prefix/extension
                .resolve(clusterName)
                .resolve(area);

        Files.createDirectories(stsDir);

        // when / then
        assertThatThrownBy(() ->
                service.processStStorageFile(
                        "cluster_battery_test",
                        "2020-2030",
                        1,
                        false,
                        area,
                        technology
                )
        )
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No ST Storage data found");
    }

    @Test
    void shouldSaveRowWhenSeriesIsTrueAndStsDirectoryIsPresentAndRequierdFilesArePresent() throws IOException {
        String horizon = "2030";
        String technology = "battery";
        String area = "FR";
        String clusterName = "cluster1";
        Path xlsx = createValidWorkbook("2030", true);
        placeInClusters(xlsx, technology, "cluster_battery_test.xlsx");

        when(userService.getCurrentUserDetails())
                .thenReturn(new UserInfoDto("nni123", null, null, null));

        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyOrderByVersionDesc(
                any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        when(trajectoryRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        // Create  STS directory
        Path stsDir = tempDir
                .resolve("trajectories")
                .resolve("STS")
                .resolve(technology)
                .resolve("series")
                .resolve("test") // filename without prefix/extension
                .resolve(clusterName)
                .resolve(area);

        Files.createDirectories(stsDir);
        // create required files

        Files.createFile(stsDir.resolve("inflows.xlsx"));
        Files.createFile(stsDir.resolve("lower_curve.xlsx"));
        Files.createFile(stsDir.resolve("Pmax_injection.xlsx"));
        Files.createFile(stsDir.resolve("Pmax_soutirage.xlsx"));
        Files.createFile(stsDir.resolve("upper_curve.xlsx"));


        TrajectoryEntity trajectory = service.processStStorageFile(
                "cluster_battery_test",
                "2020-2030",
                1,
                false,
                "FR",
                "battery"
        );

        assertThat(trajectory).isNotNull();
        assertThat(trajectory.getStStorageEntities()).hasSize(1);

        StStorageEntity entity = trajectory.getStStorageEntities().get(0);
        assertThat(entity.getArea()).isEqualTo("FR");
        assertThat(entity.getName()).isEqualTo("cluster1");
        assertThat(entity.getInjection()).isEqualByComparingTo(BigDecimal.valueOf(10));
        assertThat(entity.getSeries()).isTrue();
    }

    @Test
    void shouldSaveRowWhenSeriesIsTrueAndStsDirectoryIsPresentAndOneOfRequiredFilesIsMissing() throws IOException {
        String horizon = "2030";
        String technology = "battery";
        String area = "FR";
        String clusterName = "cluster1";
        Path xlsx = createValidWorkbook("2030", true);
        placeInClusters(xlsx, technology, "cluster_battery_test.xlsx");

        when(userService.getCurrentUserDetails())
                .thenReturn(new UserInfoDto("nni123", null, null, null));

        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyOrderByVersionDesc(
                any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        when(trajectoryRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        // Create  STS directory
        Path stsDir = tempDir
                .resolve("trajectories")
                .resolve("STS")
                .resolve(technology)
                .resolve("series")
                .resolve("test") // filename without prefix/extension
                .resolve(clusterName)
                .resolve(area);

        Files.createDirectories(stsDir);
        // create required files (one missing)
        Files.createFile(stsDir.resolve("inflows.xlsx"));
        Files.createFile(stsDir.resolve("lower_curve.xlsx"));
        Files.createFile(stsDir.resolve("Pmax_injection.xlsx"));
        Files.createFile(stsDir.resolve("Pmax_soutirage.xlsx"));


        // when / then
        assertThatThrownBy(() ->
                service.processStStorageFile(
                        "cluster_battery_test",
                        "2020-2030",
                        1,
                        false,
                        area,
                        technology
                )
        )
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No ST Storage data found");
    }



    private Path createWorkbookWithoutSheet(String horizon) throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook();
             OutputStream os = Files.newOutputStream(file)) {
            wb.createSheet("OTHER");
            wb.write(os);
        }
        return file;
    }

    private Path createWorkbookWithHeaderOnly(String horizon) throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook();
             OutputStream os = Files.newOutputStream(file)) {

            Sheet sheet = wb.createSheet(horizon);
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("area");
            header.createCell(1).setCellValue("cluster");

            wb.write(os);
        }
        return file;
    }

    private Path createValidWorkbook(String horizon ,boolean  series) throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook();
             OutputStream os = Files.newOutputStream(file)) {

            Sheet sheet = wb.createSheet(horizon);

            // header
            sheet.createRow(0);

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("FR");
            row.createCell(1).setCellValue("cluster1");
            row.createCell(2).setCellValue("group");
            row.createCell(3).setCellValue(10);
            row.createCell(4).setCellValue(5);
            row.createCell(5).setCellValue(100);
            row.createCell(6).setCellValue(0.9);
            row.createCell(7).setCellValue(95);
            row.createCell(8).setCellValue(20);
            row.createCell(9).setCellValue(true);
            row.createCell(10).setCellValue(true);
            row.createCell(11).setCellValue(series);
            row.createCell(12).setCellValue(true);

            wb.write(os);
        }
        return file;
    }

    private Path createWorkbookWithSeriesTrue(String horizon, String area, String cluster)
            throws IOException {

        Path file = tempDir.resolve("test.xlsx");

        try (Workbook wb = new XSSFWorkbook();
             OutputStream os = Files.newOutputStream(file)) {

            Sheet sheet = wb.createSheet(horizon);
            sheet.createRow(0); // header

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(area);
            row.createCell(1).setCellValue(cluster);
            row.createCell(2).setCellValue("group");
            row.createCell(3).setCellValue(10);
            row.createCell(4).setCellValue(5);
            row.createCell(5).setCellValue(100);
            row.createCell(6).setCellValue(0.9);
            row.createCell(7).setCellValue(95);
            row.createCell(8).setCellValue(20);
            row.createCell(9).setCellValue(true);
            row.createCell(10).setCellValue(true);
            row.createCell(11).setCellValue(true);  // 🔴 series = true
            row.createCell(12).setCellValue(true);

            wb.write(os);
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

}
