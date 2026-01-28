package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.WarningRepository;
import com.rte_france.antares.datamanager_back.repository.model.StStorageEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.WarningMessageEntity;
import com.rte_france.antares.datamanager_back.service.StStorage.StStorageFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

import static com.rte_france.antares.datamanager_back.util.Utils.OTHERS_AREA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StStorageFileProcessorServiceImplTest {

    private AntaressDataManagerProperties properties;
    private TrajectoryRepository trajectoryRepository;
    private UserService userService;
    private AreaRepository areaRepository;
    private StudyRepository studyRepository;
    private WarningRepository warningRepository;

    private StStorageFileProcessorServiceImpl service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        properties = mock(AntaressDataManagerProperties.class);
        trajectoryRepository = mock(TrajectoryRepository.class);
        userService = mock(UserService.class);
        areaRepository = mock(AreaRepository.class);
        studyRepository = mock(StudyRepository.class);
        warningRepository = mock(WarningRepository.class);

        // Construct service with current constructor (properties, trajectoryRepository, userService, areaRepository, studyRepository)
        service = new StStorageFileProcessorServiceImpl(
                properties,
                trajectoryRepository,
                userService,
                areaRepository,
                studyRepository
        );

        when(properties.getNasDirectory()).thenReturn(tempDir.toString());
        when(properties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(properties.getStsDirectory()).thenReturn("STS");

        // default study areas
        when(areaRepository.findAllByStudyId(anyInt()))
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
                service.processStStorageFile("cluster_battery_test.xlsx", "2029-2030", 1, false, "FR", "battery")
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not exist in the STS trajectory");
    }

    @Test
    void shouldThrowExceptionWhenNoValidRowsFound() throws IOException {
        Path xlsx = createWorkbookWithHeaderOnly("2030");
        placeInClusters(xlsx, "battery", "cluster_battery_test.xlsx");

        assertThatThrownBy(() ->
                service.processStStorageFile("cluster_battery_test.xlsx", "2029-2030", 1, false, "FR", "battery")
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("None of the areas of trajectory AREA are present in STS trajectory");
    }

    @Test
    void shouldCreateTrajectoryAndEntitiesSuccessfully() throws IOException {
        Path xlsx = createValidWorkbook("2030", false);
        placeInClusters(xlsx, "battery", "cluster_battery_test.xlsx");

        // stubs for repository/user
        when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{
            setNni("TESTNNI");
        }});
        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyOrderByVersionDesc(
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenAnswer((Answer<TrajectoryEntity>) inv -> inv.getArgument(0));

        TrajectoryEntity trajectory = service.processStStorageFile(
                "cluster_battery_test.xlsx", "2029-2030", 1, false, "FR", "battery"
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
    void shouldIgnoreRowWhenSeriesIsTrueAndTsFilesAreMissing() throws IOException {
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
                .resolve("cluster_battery_test")
                .resolve(clusterName)
                .resolve(area);

        Files.createDirectories(stsDir);

        // no required files -> row ignored and therefore no valid rows -> exception
        assertThatThrownBy(() ->
                service.processStStorageFile("cluster_battery_test.xlsx", horizon, 1, false, area, technology)
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Can not import : Missing TS for trajectory");
    }

    @Test
    void shouldThrowWhenNonNumericValuesInNumericColumns() throws IOException {
        Path xlsx = createWorkbookWithNonNumeric("2030");
        placeInClusters(xlsx, "battery", "cluster_battery_test.xlsx");

        assertThatThrownBy(() ->
                service.processStStorageFile("cluster_battery_test.xlsx", "2029-2030", 1, false, "FR", "battery")
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Values for node");
    }

    @Test
    void shouldThrowWhenOTHERSAreaNonePresent() throws IOException {
        // study areas contain only FR (set in setUp), file contains area XX -> no study area found -> exception
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("2030");
            Row header = s.createRow(0);
            String[] headers = {"Area", "Name", "Group", "Injection", "Withdrawal", "Storage", "Efficiency_injection", "Efficiency_withdrawal", "Initial_level", "Initial_level_optim", "Enabled", "Series", "Constraints"};
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
                .hasMessageContaining("None of the areas of trajectory AREA are present in STS trajectory");
    }

    @Test
    void shouldProcessWhenOTHERSAreaAndSomeStudyAreasMissing() throws IOException {
        // study has FR and DE
        when(areaRepository.findAllByStudyId(anyInt()))
                .thenReturn(List.of(
                        new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                            setName("FR");
                        }},
                        new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                            setName("DE");
                        }}
                ));

        // file contains FR only -> missing DE but process should succeed (warning logic not persisted in current impl)
        Path xlsx = createValidWorkbook("2030", false);
        placeInClusters(xlsx, "battery", "cluster_battery_test.xlsx");

        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyOrderByVersionDesc(
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenAnswer((Answer<TrajectoryEntity>) inv -> inv.getArgument(0));
        when(userService.getCurrentUserDetails()).thenReturn(null);

        TrajectoryEntity trajectory = service.processStStorageFile("cluster_battery_test", "2029-2030", 1, false, "FR", "battery");
        assertThat(trajectory).isNotNull();
        assertThat(trajectory.getStStorageEntities()).hasSize(1);
    }

    @Test
    void shouldCreateWarningWhenSomeStudyAreasAreMissing() throws IOException {
        // study has FR and DE
        when(areaRepository.findAllByStudyId(anyInt()))
                .thenReturn(List.of(
                        new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                            setName("FR");
                        }},
                        new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                            setName("DE");
                        }}
                ));

        // file contains only FR
        Path xlsx = createValidWorkbook("2030", false);
        placeInClusters(xlsx, "battery", "cluster_battery_test.xlsx");

        when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{
            setNni("TESTNNI");
        }});
        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyOrderByVersionDesc(
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenAnswer((Answer<TrajectoryEntity>) inv -> inv.getArgument(0));
        when(studyRepository.findById(anyInt())).thenReturn(Optional.of(new com.rte_france.antares.datamanager_back.repository.model.StudyEntity()));

        TrajectoryEntity trajectory = service.processStStorageFile("cluster_battery_test.xlsx", "2029-2030", 1, false, OTHERS_AREA, "battery");

        Optional<WarningMessageEntity> warningOpt = trajectory.getWarningMessages().stream().findFirst();
        assertThat(warningOpt).isPresent();
        assertThat(warningOpt.get().getCreatedBy()).isEqualTo("TESTNNI");
        assertThat(warningOpt.get().getIsAck()).isFalse();
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
            String[] headers = {"Area", "Name", "Group", "Injection", "Withdrawal", "Storage", "Efficiency_injection", "Efficiency_withdrawal", "Initial_level", "Initial_level_optim", "Enabled", "Series", "Constraints"};
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
            String[] headers = {"Area", "Name", "Group", "Injection", "Withdrawal", "Storage", "Efficiency_injection", "Efficiency_withdrawal", "Initial_level", "Initial_level_optim", "Enabled", "Series", "Constraints"};
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
            r.createCell(8).setCellValue(50); // initial level
            r.createCell(9).setCellValue("true");
            r.createCell(10).setCellValue("false");
            r.createCell(11).setCellValue(series ? "true" : "false");
            r.createCell(12).setCellValue("false");

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
            String[] headers = {"Area", "Name", "Group", "Injection", "Withdrawal", "Storage", "Efficiency_injection", "Efficiency_withdrawal", "Initial_level", "Initial_level_optim", "Enabled", "Series", "Constraints"};
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
            r.createCell(8).setCellValue(50);
            r.createCell(9).setCellValue("true");
            r.createCell(10).setCellValue("false");
            r.createCell(11).setCellValue("true"); // series true
            r.createCell(12).setCellValue("false");

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
            String[] headers = {"Area", "Name", "Group", "Injection", "Withdrawal", "Storage", "Efficiency_injection", "Efficiency_withdrawal", "Initial_level", "Initial_level_optim", "Enabled", "Series", "Constraints"};
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

}
