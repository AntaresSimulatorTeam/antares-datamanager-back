package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.dsr.impl.DsrFileProcessorServiceImpl;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DsrFileProcessorServiceImplTest {

    private TrajectoryRepository trajectoryRepository;
    private UserService userService;
    private AreaRepository areaRepository;
    private DsrFileProcessorServiceImpl service;

    private static final String FILE_NAME_DSR_CLUSTER = "cluster_DSR_test.xlsx";
    private static final String[] headers = {
            "toUse", "Area", "Name", "Capacity", "Reliability", "nb_hour_per_day", "max_hour_per_day",
            "price", "nb_units", "FO_rate", "FO_duration", "Modulation"};

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        AntaresDataManagerProperties properties = mock(AntaresDataManagerProperties.class);
        trajectoryRepository = mock(TrajectoryRepository.class);
        userService = mock(UserService.class);
        areaRepository = mock(AreaRepository.class);

        // Construct service with current constructor
        service = new DsrFileProcessorServiceImpl(
                properties,
                trajectoryRepository,
                userService,
                areaRepository
        );

        when(properties.getNasDirectory()).thenReturn(tempDir.toString());
        when(properties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(properties.getDsrDirectory()).thenReturn("DSR/cluster");

        // default study areas
        when(areaRepository.findAllByStudyId(anyInt()))
                .thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                    setName("FR");
                }}));
    }

    private void placeInCluster(Path source, String targetFileName) throws IOException {
        Path clusters = tempDir
                .resolve("trajectories")
                .resolve("DSR")
                .resolve("cluster");
        Files.createDirectories(clusters);
        Path target = clusters.resolve(targetFileName);
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    void shouldThrowExceptionWhenTrajectoryNameIsInvalid() {
        assertThatThrownBy(() ->
                service.processDsrClusterFile(
                        "invalid_name",
                        "2020-2030",
                        1,
                        true,
                        "FR"
                )
        )
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("The trajectory file name must start with {0}");
    }

    @Test
    void shouldThrowExceptionWhenSheetIsMissing() throws IOException {
        Path xlsx = createWorkbookWithoutSheet();
        placeInCluster(xlsx, FILE_NAME_DSR_CLUSTER);

        assertThatThrownBy(() ->
                service.processDsrClusterFile("cluster_DSR_test", "2029-2030", 1, false, "FR")
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Horizon {0} does not exist in the DSR Cluster trajectory {1}");
    }

    @Test
    void shouldCreateTrajectoryAndEntitiesSuccessfully() throws Exception {
        Path xlsx = createValidWorkbook("2030", false);
        placeInCluster(xlsx, FILE_NAME_DSR_CLUSTER);

        // stubs for repository/user
        when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{
            setNni("TESTNNI");
        }});
        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenAnswer((Answer<TrajectoryEntity>) inv -> inv.getArgument(0));

        TrajectoryEntity trajectory = service.processDsrClusterFile(
                "cluster_DSR_test", "2029-2030", 1, false, "FR"
        );

        assertThat(trajectory).isNotNull();
        assertThat(trajectory.getDsrClusterEntities()).hasSize(1);
        assertThat(trajectory.getHasTimeSeries()).isFalse();

        DsrClusterEntity entity = trajectory.getDsrClusterEntities().getFirst();
        assertThat(entity.getArea()).isEqualTo("FR");
        assertThat(entity.getName()).isEqualTo("DSR_industries");
        assertThat(entity.getCapacity()).isEqualByComparingTo(BigDecimal.valueOf(2000.0));
    }

    @Test
    void shouldCreateTrajectoryWithIncrementVersionWhenTrajectoryExists() throws Exception {
        Path xlsx = createValidWorkbook("2030", false);
        placeInCluster(xlsx, "cluster_DSR_test.xlsx");

        // stubs for repository/user
        UserInfoDto user = new UserInfoDto();
        user.setNni("TESTNNI");
        when(userService.getCurrentUserDetails()).thenReturn(user);

        var trajectoryEntity = new TrajectoryEntity();
        trajectoryEntity.setType(TrajectoryType.DSR.name());
        trajectoryEntity.setArea("FR");
        trajectoryEntity.setFileName("test");
        trajectoryEntity.setVersion(1);
        trajectoryEntity.setHorizon("2029-2030");
        trajectoryEntity.setChecksum("ABC123");
        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(trajectoryEntity));
        when(trajectoryRepository.save(any())).thenAnswer((Answer<TrajectoryEntity>) inv -> inv.getArgument(0));

        TrajectoryEntity trajectory = service.processDsrClusterFile(
                "cluster_DSR_test", "2029-2030", 1, false, "FR"
        );

        assertThat(trajectory).isNotNull();
        assertThat(trajectory.getVersion()).isEqualTo(2);
    }

    @Test
    void shouldThrowWhenNonNumericValuesInNumericColumns() throws IOException {
        Path xlsx = createWorkbookWithNonNumeric("2030");
        placeInCluster(xlsx, FILE_NAME_DSR_CLUSTER);

        assertThatThrownBy(() ->
                service.processDsrClusterFile("cluster_DSR_test", "2029-2030", 1, false, "FR")
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Values Capacity, Reliability, price, FO_rate for node {0} / cluster {1} must be numeric in DSR Cluster trajectory {2}");
    }

    @Test
    void shouldThrowWhenNonIntegerValuesInIntegerColumns() throws IOException {
        Path xlsx = createWorkbookWithNonInteger("2030");
        placeInCluster(xlsx, FILE_NAME_DSR_CLUSTER);

        assertThatThrownBy(() ->
                service.processDsrClusterFile("cluster_DSR_test", "2029-2030", 1, false, "FR")
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Values nb_hour_per_day, max_hour_per_day, nb_units, FO_duration for node {0} / cluster {1} must be integer in DSR Cluster trajectory {2}");
    }

    @Test
    void shouldThrowWhenNonBooleanValueInBooleanColumn() throws IOException {
        Path xlsx = createWorkbookWithNonBoolean("2030");
        placeInCluster(xlsx, FILE_NAME_DSR_CLUSTER);

        assertThatThrownBy(() ->
                service.processDsrClusterFile("cluster_DSR_test", "2029-2030", 1, false, "FR")
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Modulation for node {0} / cluster {1} are not boolean in DSR trajectory {2}");
    }

    @Test
    void shouldThrowWhenClusterNameValueExceedsMaxNbCharacters() throws IOException {
        Path xlsx = createWorkbookWithClusterNameTooLong("2030");
        placeInCluster(xlsx, FILE_NAME_DSR_CLUSTER);

        assertThatThrownBy(() ->
                service.processDsrClusterFile("cluster_DSR_test", "2029-2030", 1, false, "FR")
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Value {0} too long in DSR Cluster trajectory {1} for area {2} and horizon {3}");
    }

    @Test
    void shouldThrowExceptionWhenNoValidRowsFound() throws IOException {
        Path xlsx = createWorkbookWithHeaderOnly("2030");
        placeInCluster(xlsx, "cluster_DSR_test.xlsx");

        assertThatThrownBy(() ->
                service.processDsrClusterFile("cluster_DSR_test", "2029-2030", 1, false, "FR")
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("No data in DSR Cluster trajectory {0} for horizon: {1}");
    }

    @Test
    void shouldThrowWhenOTHERSAreaNonePresent() throws IOException {
        // study areas contain only FR (set in setUp), file contains area XX -> no study area found -> exception
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("2030");
            Row header = s.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            Row row = s.createRow(1);
            row.createCell(0).setCellValue(1);
            row.createCell(1).setCellValue("XX"); // Area
            row.createCell(2).setCellValue("DSR_industrie");
            row.createCell(3).setCellValue(2000);
            row.createCell(4).setCellValue("100%");
            row.createCell(5).setCellValue(12);
            row.createCell(6).setCellValue(8);
            row.createCell(7).setCellValue(200);
            row.createCell(8).setCellValue(80);
            row.createCell(9).setCellValue(0.8);
            row.createCell(10).setCellValue(1);
            row.createCell(11).setCellValue("TRUE");

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        placeInCluster(file, FILE_NAME_DSR_CLUSTER);

        assertThatThrownBy(() ->
                service.processDsrClusterFile("cluster_DSR_test", "2029-2030", 1, false, "OTHERS_AREA")
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("None of the areas of trajectory AREA are present in {0} trajectory {1}");
    }

    @Test
    void shouldProcessWhenOTHERSAreaAndSomeStudyAreasMissing() throws Exception {
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

        // file contains FR only -> missing DE but process should succeed
        Path xlsx = createValidWorkbook("2030", false);
        placeInCluster(xlsx, FILE_NAME_DSR_CLUSTER);

        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenAnswer((Answer<TrajectoryEntity>) inv -> inv.getArgument(0));
        when(userService.getCurrentUserDetails()).thenReturn(null);

        TrajectoryEntity trajectory = service.processDsrClusterFile("cluster_DSR_test", "2029-2030", 1, false, "FR");
        assertThat(trajectory).isNotNull();
        assertThat(trajectory.getDsrClusterEntities()).hasSize(1);
        assertThat(trajectory.getHasTimeSeries()).isFalse();
    }

    @Test
    void shouldThrowWhenNoStudyAreaIsPresentInDsrFile() throws Exception {
        // GIVEN
        String horizon = "2025";
        String areaParam = "IT";

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

        // Create a fake DSR Excel file in temp
        Path tempFile = Files.createTempFile("cluster_DSR_", ".xlsx");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(horizon);

            // Header
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            // Row with ONLY area IT (not in study)
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(1);
            row.createCell(1).setCellValue("IT"); // Area
            row.createCell(2).setCellValue("DSR_industries");
            row.createCell(3).setCellValue(2000);
            row.createCell(4).setCellValue(0.5);
            row.createCell(5).setCellValue(12);
            row.createCell(6).setCellValue(8);
            row.createCell(7).setCellValue(200);
            row.createCell(8).setCellValue(80);
            row.createCell(9).setCellValue(0.8);
            row.createCell(10).setCellValue(1);
            row.createCell(11).setCellValue("TRUE");

            try (OutputStream os = Files.newOutputStream(tempFile)) {
                workbook.write(os);
            }
        }

        // Spy service to bypass file search
        DsrFileProcessorServiceImpl serviceSpy = spy(service);

        doReturn(tempFile)
                .when(serviceSpy)
                .getTrajectoryFilePath(anyString());

        // WHEN / THEN
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> serviceSpy.processDsrClusterFile(
                        FILE_NAME_DSR_CLUSTER,
                        "horizon-2025",
                        1,
                        false,
                        areaParam
                )
        );

        assertTrue(ex.getMessage().contains("None of the areas of trajectory AREA are present in {0} trajectory {1}"));
    }

    @Test
    void shouldThrowWhenSelectedAreaIsNotPresentInDsrFile() throws Exception {
        // GIVEN
        String horizon = "2025";
        String areaParam = "IT";

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

        // Create a fake DSR Excel file in temp
        Path tempFile = Files.createTempFile("cluster_DSR_", ".xlsx");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(horizon);

            // Header
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            // Row with ONLY area IT (not in study)
            Row row0 = sheet.createRow(1);
            row0.createCell(0).setCellValue(1);
            row0.createCell(1).setCellValue("IT"); // Area
            row0.createCell(2).setCellValue("DSR_industries");
            row0.createCell(3).setCellValue(2000);
            row0.createCell(4).setCellValue(0.5);
            row0.createCell(5).setCellValue(12);
            row0.createCell(6).setCellValue(8);
            row0.createCell(7).setCellValue(200);
            row0.createCell(8).setCellValue(80);
            row0.createCell(9).setCellValue(0.8);
            row0.createCell(10).setCellValue(1);
            row0.createCell(11).setCellValue("TRUE");

            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue(1);
            row1.createCell(1).setCellValue("DE");
            row1.createCell(2).setCellValue("DSR_tertiaire");
            row1.createCell(3).setCellValue(2000);
            row1.createCell(4).setCellValue(0.5);
            row1.createCell(5).setCellValue(12);
            row1.createCell(6).setCellValue(8);
            row1.createCell(7).setCellValue(200);
            row1.createCell(8).setCellValue(80);
            row1.createCell(9).setCellValue(0.8);
            row1.createCell(10).setCellValue(1);
            row1.createCell(11).setCellValue("TRUE");

            try (OutputStream os = Files.newOutputStream(tempFile)) {
                workbook.write(os);
            }
        }

        // Spy service to bypass file search
        DsrFileProcessorServiceImpl serviceSpy = spy(service);

        doReturn(tempFile)
                .when(serviceSpy)
                .getTrajectoryFilePath(anyString());

        // WHEN / THEN
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> serviceSpy.processDsrClusterFile(
                        FILE_NAME_DSR_CLUSTER,
                        "horizon-2025",
                        1,
                        false,
                        areaParam
                )
        );

        assertTrue(ex.getMessage().contains("Selected area {0} is not present in the 'node' column of {1} trajectory {2}"));
    }

    @Test
    void shouldThrowWhenMissingColumnsInDsrFile() throws Exception {
        String horizon = "2025";
        String areaParam = "FR";

        // studyAreas peu importe ici, on ne va jamais jusque-là
        when(areaRepository.findAllByStudyId(anyInt()))
                .thenReturn(List.of());

        // Création d’un Excel avec header incomplet
        Path tempFile = Files.createTempFile("cluster_DSR", ".xlsx");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(horizon);

            Row header = sheet.createRow(0);

            // On met volontairement QUE 3 colonnes au lieu des 13 attendues
            header.createCell(0).setCellValue("to_use");
            header.createCell(1).setCellValue("Area");
            header.createCell(2).setCellValue("Name");
            // Toutes les autres colonnes manquent

            try (OutputStream os = Files.newOutputStream(tempFile)) {
                workbook.write(os);
            }
        }

        // Spy pour bypass la recherche NAS
        DsrFileProcessorServiceImpl serviceSpy = spy(service);
        doReturn(tempFile)
                .when(serviceSpy)
                .getTrajectoryFilePath(anyString());

        // WHEN / THEN
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> serviceSpy.processDsrClusterFile(
                        "cluster_DSR_test",
                        "horizon-2025",
                        1,
                        false,
                        areaParam
                )
        );

        assertTrue(ex.getMessage().contains("Missing columns"));
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
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }

    private Path createValidWorkbook(String horizon, boolean series) throws IOException {
        Path file = tempDir.resolve("cluster_DSR_test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet(horizon);
            Row header = s.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            Row row = s.createRow(1);
            row.createCell(0).setCellValue(1);
            row.createCell(1).setCellValue("FR");
            row.createCell(2).setCellValue("DSR_industries");
            row.createCell(3).setCellValue(2000);
            row.createCell(4).setCellValue(0.5);
            row.createCell(5).setCellValue(12);
            row.createCell(6).setCellValue(8);
            row.createCell(7).setCellValue(200);
            row.createCell(8).setCellValue(80);
            row.createCell(9).setCellValue(0.8);
            row.createCell(10).setCellValue(1);
            row.createCell(11).setCellValue(series ? "TRUE" : "FALSE");

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
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            Row row = s.createRow(1);
            row.createCell(0).setCellValue(1);
            row.createCell(1).setCellValue("FR");
            row.createCell(2).setCellValue("DSR_industries");
            row.createCell(3).setCellValue("pas_numeric");
            row.createCell(4).setCellValue("pas_numeric");
            row.createCell(5).setCellValue(12);
            row.createCell(6).setCellValue(8);
            row.createCell(7).setCellValue("pas_numeric");
            row.createCell(8).setCellValue(80);
            row.createCell(9).setCellValue("pas_numeric");
            row.createCell(10).setCellValue(1);
            row.createCell(11).setCellValue("FALSE");

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }

    private Path createWorkbookWithNonInteger(String horizon) throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet(horizon);
            Row header = s.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            Row row = s.createRow(1);
            row.createCell(0).setCellValue(1);
            row.createCell(1).setCellValue("FR");
            row.createCell(2).setCellValue("DSR_industries");
            row.createCell(3).setCellValue(2000);
            row.createCell(4).setCellValue(0.5);
            row.createCell(5).setCellValue("pas_integer");
            row.createCell(6).setCellValue("pas_integer");
            row.createCell(7).setCellValue(200);
            row.createCell(8).setCellValue("pas_integer");
            row.createCell(9).setCellValue(0.8);
            row.createCell(10).setCellValue("pas_integer");
            row.createCell(11).setCellValue("FALSE");

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }

    private Path createWorkbookWithNonBoolean(String horizon) throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet(horizon);
            Row header = s.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            Row row = s.createRow(1);
            row.createCell(0).setCellValue(1);
            row.createCell(1).setCellValue("FR");
            row.createCell(2).setCellValue("DSR_industries");
            row.createCell(3).setCellValue(2000);
            row.createCell(4).setCellValue(0.5);
            row.createCell(5).setCellValue(12);
            row.createCell(6).setCellValue(8);
            row.createCell(7).setCellValue(200);
            row.createCell(8).setCellValue(80);
            row.createCell(9).setCellValue(0.8);
            row.createCell(10).setCellValue(1);
            row.createCell(11).setCellValue("pas_boolean");

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }

    private Path createWorkbookWithClusterNameTooLong(String horizon) throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet(horizon);
            Row header = s.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            Row row = s.createRow(1);
            row.createCell(0).setCellValue(1);
            row.createCell(1).setCellValue("FR");
            row.createCell(2).setCellValue("DSR_industries_is_very_too_long_very_too_long_very_too_long");
            row.createCell(3).setCellValue(2000);
            row.createCell(4).setCellValue(0.5);
            row.createCell(5).setCellValue(12);
            row.createCell(6).setCellValue(8);
            row.createCell(7).setCellValue(200);
            row.createCell(8).setCellValue(80);
            row.createCell(9).setCellValue(0.8);
            row.createCell(10).setCellValue(1);
            row.createCell(11).setCellValue("TRUE");

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        return file;
    }
}
