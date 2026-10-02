package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.HydroAllocationMeRepository;
import com.rte_france.antares.datamanager_back.repository.HydroCapacityMeRepository;
import com.rte_france.antares.datamanager_back.repository.HydroParametersMeRepository;
import com.rte_france.antares.datamanager_back.repository.model.HydroAllocationMeEntity;
import com.rte_france.antares.datamanager_back.repository.model.HydroCapacityMeEntity;
import com.rte_france.antares.datamanager_back.repository.model.HydroParametersMeEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.study.impl.HydroMeToJsonService;
import com.rte_france.antares.datamanager_back.util.PathSecurityUtil;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrix;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrixColumn;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesReader;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HydroMeToJsonServiceTest {

    @Mock
    private HydroCapacityMeRepository hydroCapacityMeRepository;

    @Mock
    private HydroParametersMeRepository hydroParametersMeRepository;

    @Mock
    private HydroAllocationMeRepository hydroAllocationMeRepository;

    @Mock
    private NasFileService nasFileService;

    @Mock
    private TimeSeriesReader timeSeriesReader;

    @Mock
    private AntaresDataManagerProperties antaresDataManagerProperties;

    private HydroMeToJsonService hydroMeToJsonService;

    @TempDir
    Path nasDir;

    private Path generatingTsFile;
    private Path pumpingTsFile;

    private final TrajectoryEntity trajectory = TrajectoryEntity.builder()
            .id(1)
            .fileName("hydro_capacity_me_traj")
            .checksum("capacity-checksum")
            .horizon("2026-2027")
            .build();

    @BeforeEach
    void setUp() throws IOException {
        hydroMeToJsonService = new HydroMeToJsonService(
                hydroCapacityMeRepository, hydroParametersMeRepository, hydroAllocationMeRepository,
                nasFileService, timeSeriesReader, antaresDataManagerProperties,
                new PathSecurityUtil(antaresDataManagerProperties));
        Path capaStorage = nasDir.resolve("INPUT").resolve("ME/hydro_ME/capa_storage");
        generatingTsFile = createFile(capaStorage.resolve("Generating Pmax daily ts").resolve("hydro_capacity_me_traj.xlsx"));
        pumpingTsFile = createFile(capaStorage.resolve("Pumping Pmax daily ts").resolve("hydro_capacity_me_traj.xlsx"));
        lenient().when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir.toString());
        lenient().when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("INPUT");
        lenient().when(antaresDataManagerProperties.getHydroCapacityMeDirectory()).thenReturn("ME/hydro_ME/capa_storage");
        lenient().when(antaresDataManagerProperties.getHydroReservoirLevelsMeDirectory())
                .thenReturn("ME/hydro_ME/reservoir_levels");
        lenient().when(antaresDataManagerProperties.getHydroTimeSeriesMeDirectory())
                .thenReturn("ME/hydro_ME/timeseries");
        lenient().when(antaresDataManagerProperties.getHydroWaterValuesMeDirectory())
                .thenReturn("ME/hydro_ME/water_values");
        lenient().when(antaresDataManagerProperties.getHydroMeOutputDirectory()).thenReturn("output/hydro_ME");
        lenient().when(nasFileService.saveMatrixToNas(any(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(1) + ".uuid.arrow");
        lenient().when(nasFileService.saveMatrixToNasWithName(any(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(1));
    }

    private static Path createFile(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.createFile(file);
    }

    private static TimeSeriesMatrix matrixOf(String column, int size) {
        double[] values = new double[size];
        for (int i = 0; i < size; i++) {
            values[i] = i;
        }
        return new TimeSeriesMatrix(List.of(new TimeSeriesMatrixColumn(column, values)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"reservoir_levels", "timeseries", "water_values"})
    void buildHydroMeMap_trajectoryDirectorySymlinkOutsideNas_shouldReject(
            String directory, @TempDir Path outsideNas) throws IOException {
        Path base = nasDir.resolve("INPUT/ME/hydro_ME").resolve(directory);
        Files.createDirectories(base);
        Files.createSymbolicLink(base.resolve("unsafe_trajectory"), outsideNas);
        assertUnsafeHydroTrajectoryRejected(directory);
    }

    @ParameterizedTest
    @ValueSource(strings = {"reservoir_levels", "ror", "mod", "water_values", "node_directory"})
    void buildHydroMeMap_listedSymlinkOutsideNas_shouldReject(
            String target, @TempDir Path outsideNas) throws IOException {
        String directory = switch (target) {
            case "reservoir_levels", "water_values" -> target;
            default -> "timeseries";
        };
        Path trajectoryDirectory = nasDir.resolve("INPUT/ME/hydro_ME")
                .resolve(directory).resolve("unsafe_trajectory");
        Files.createDirectories(trajectoryDirectory);
        if (target.equals("node_directory")) {
            Files.createSymbolicLink(trajectoryDirectory.resolve("node_a"), outsideNas);
        } else {
            String filename = switch (target) {
                case "reservoir_levels" -> "node_a_reservoir_levels.xlsx";
                case "water_values" -> "node_a_water_values.xlsx";
                default -> target + ".xlsx";
            };
            Path parent = directory.equals("timeseries")
                    ? trajectoryDirectory.resolve("node_a") : trajectoryDirectory;
            Files.createDirectories(parent);
            Path externalFile = Files.createFile(outsideNas.resolve(filename));
            Files.createSymbolicLink(parent.resolve(filename), externalFile);
        }
        assertUnsafeHydroTrajectoryRejected(directory);
    }

    private void assertUnsafeHydroTrajectoryRejected(String directory) {
        when(hydroCapacityMeRepository.findByTrajectoryIdOrderByNodeAsc(1)).thenReturn(List.of(
                HydroCapacityMeEntity.builder().node("node_a").build()));
        TrajectoryEntity unsafeTrajectory = TrajectoryEntity.builder()
                .fileName("unsafe_trajectory").checksum("checksum").horizon("2026-2027").build();

        assertThatThrownBy(() -> hydroMeToJsonService.buildHydroMeMap(
                trajectory, null,
                directory.equals("reservoir_levels") ? unsafeTrajectory : null,
                directory.equals("timeseries") ? unsafeTrajectory : null,
                directory.equals("water_values") ? unsafeTrajectory : null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Resolved path is outside of the allowed directory");
        verifyNoInteractions(timeSeriesReader, nasFileService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"generating", "pumping"})
    void buildHydroMeMap_dailyFileSymlinkOutsideNas_shouldRejectBeforeReading(
            String kind, @TempDir Path outsideNas) throws IOException {
        Path tsFile = kind.equals("generating") ? generatingTsFile : pumpingTsFile;
        Path externalFile = Files.createFile(outsideNas.resolve("external.xlsx"));
        Files.delete(tsFile);
        Files.createSymbolicLink(tsFile, externalFile);
        when(hydroCapacityMeRepository.findByTrajectoryIdOrderByNodeAsc(1)).thenReturn(List.of(
                HydroCapacityMeEntity.builder().node("node_a")
                        .generatingPmaxTimestep(kind.equals("generating") ? "daily" : "annual")
                        .pumpingPmaxTimestep(kind.equals("pumping") ? "daily" : "annual")
                        .build()));

        assertThatThrownBy(() -> hydroMeToJsonService.buildHydroMeMap(trajectory))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Resolved path is outside of the allowed directory");
        verifyNoInteractions(timeSeriesReader, nasFileService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"generating", "pumping"})
    void buildHydroMeMap_dailyDirectorySymlinkOutsideNas_shouldRejectBeforeReading(
            String kind, @TempDir Path outsideNas) throws IOException {
        Path tsFile = kind.equals("generating") ? generatingTsFile : pumpingTsFile;
        Files.createFile(outsideNas.resolve(tsFile.getFileName()));
        Files.delete(tsFile);
        Files.delete(tsFile.getParent());
        Files.createSymbolicLink(tsFile.getParent(), outsideNas);
        when(hydroCapacityMeRepository.findByTrajectoryIdOrderByNodeAsc(1)).thenReturn(List.of(
                HydroCapacityMeEntity.builder().node("node_a")
                        .generatingPmaxTimestep(kind.equals("generating") ? "daily" : "annual")
                        .pumpingPmaxTimestep(kind.equals("pumping") ? "daily" : "annual")
                        .build()));

        assertThatThrownBy(() -> hydroMeToJsonService.buildHydroMeMap(trajectory))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Resolved path is outside of the allowed directory");
        verifyNoInteractions(timeSeriesReader, nasFileService);
    }

    private Path createReservoirLevelsFile(String trajectoryName, String[] headers) throws IOException {
        Path file = nasDir.resolve("INPUT").resolve("ME/hydro_ME/reservoir_levels")
                .resolve(trajectoryName).resolve("node_a_reservoir_levels.xlsx");
        Files.createDirectories(file.getParent());
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("2027");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            Row firstDataRow = sheet.createRow(1);
            firstDataRow.createCell(0).setCellValue("2026-10-01");
            firstDataRow.createCell(1).setCellValue(1.25);
            firstDataRow.createCell(2).setCellValue(2.5);
            firstDataRow.createCell(3).setCellValue(3.75);
            Row secondDataRow = sheet.createRow(2);
            secondDataRow.createCell(0).setCellValue("2026-10-02");
            secondDataRow.createCell(1).setCellValue(4.25);
            secondDataRow.createCell(2).setCellValue(5.5);
            secondDataRow.createCell(3).setCellValue(6.75);
            try (var output = Files.newOutputStream(file)) {
                workbook.write(output);
            }
        }
        return file;
    }

    private Path createHydroTimeSeriesFile(String trajectoryName, String node, String series) throws IOException {
        Path file = nasDir.resolve("INPUT").resolve("ME/hydro_ME/timeseries")
                .resolve(trajectoryName).resolve(node).resolve(series + ".xlsx");
        Files.createDirectories(file.getParent());
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("2027");
            sheet.createRow(0).createCell(0).setCellValue(series.equals("ror") ? 10.5 : 20.5);
            sheet.getRow(0).createCell(1).setCellValue(999);
            sheet.createRow(1).createCell(0).setCellValue(series.equals("ror") ? 11.5 : 21.5);
            sheet.getRow(1).createCell(1).setCellValue(999);
            try (var output = Files.newOutputStream(file)) {
                workbook.write(output);
            }
        }
        return file;
    }

    private Path createWaterValuesFile(String trajectoryName, String node) throws IOException {
        Path file = nasDir.resolve("INPUT").resolve("ME/hydro_ME/water_values")
                .resolve(trajectoryName).resolve(node + "_water_values.xlsx");
        Files.createDirectories(file.getParent());
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("2027");
            sheet.createRow(0).createCell(0).setCellValue(10.5);
            sheet.getRow(0).createCell(1).setCellValue(20.5);
            sheet.createRow(1).createCell(0).setCellValue(11.5);
            sheet.getRow(1).createCell(1).setCellValue(21.5);
            try (var output = Files.newOutputStream(file)) {
                workbook.write(output);
            }
        }
        return file;
    }

    @ParameterizedTest
    @ValueSource(strings = {"waterValues_node10.xlsx", "node10_water_values.xlsx"})
    void buildHydroMeMap_waterValuesOverlappingNodes_shouldAttachOnlyExactNode(String filename) throws IOException {
        Path file = createWaterValuesFile("water_trajectory", "node10");
        file = Files.move(file, file.resolveSibling(filename));
        when(timeSeriesReader.readFromXlsx(file, "2027", false))
                .thenReturn(matrixOf("Column0", 2));
        when(hydroCapacityMeRepository.findByTrajectoryIdOrderByNodeAsc(1)).thenReturn(List.of(
                HydroCapacityMeEntity.builder().node("AT").build(),
                HydroCapacityMeEntity.builder().node("node1").build(),
                HydroCapacityMeEntity.builder().node("node10").build()));
        TrajectoryEntity waterTrajectory = TrajectoryEntity.builder()
                .fileName("water_trajectory").horizon("2026-2027").checksum("watercheck").build();

        Map<String, Object> result =
                hydroMeToJsonService.buildHydroMeMap(trajectory, null, null, null, waterTrajectory);

        Map<String, Object> properties = Collections.singletonMap("reservoir_capacity", null);
        assertThat(result.get("AT")).isEqualTo(Map.of("properties", properties));
        assertThat(result.get("node1")).isEqualTo(Map.of("properties", properties));
        assertThat(result.get("node10")).isEqualTo(Map.of(
                "properties", properties, "water_values_ts", "node10_watercheck.arrow"));
        verify(nasFileService).saveMatrixToNasWithName(
                any(), eq("node10_watercheck.arrow"), eq("output/hydro_ME"));
    }

    @Test
    void buildHydroMeMap_annualTimesteps_shouldAddPmaxAndHours() {
        when(hydroCapacityMeRepository.findByTrajectoryIdOrderByNodeAsc(1)).thenReturn(List.of(
                HydroCapacityMeEntity.builder()
                        .node("node_a")
                        .reservoirCapacity(BigDecimal.valueOf(5376))
                        .generatingPmaxTimestep("annual")
                        .generatingPmax(BigDecimal.valueOf(100))
                        .hoursAtGeneratingPmax(BigDecimal.valueOf(24))
                        .pumpingPmaxTimestep("Annual")
                        .pumpingPmax(BigDecimal.valueOf(50))
                        .hoursAtPumpingPmax(BigDecimal.valueOf(12))
                        .build()));

        Map<String, Object> result = hydroMeToJsonService.buildHydroMeMap(trajectory);

        @SuppressWarnings("unchecked")
        Map<String, Object> node = (Map<String, Object>) result.get("node_a");
        assertThat(node).containsOnlyKeys("properties", "generating_pmax", "pumping_pmax");
        assertThat(node.get("properties")).isEqualTo(Map.of("reservoir_capacity", BigDecimal.valueOf(5376)));
        assertThat(node.get("generating_pmax")).isEqualTo(Map.of("pmax", BigDecimal.valueOf(100), "hours", BigDecimal.valueOf(24)));
        assertThat(node.get("pumping_pmax")).isEqualTo(Map.of("pmax", BigDecimal.valueOf(50), "hours", BigDecimal.valueOf(12)));
    }

    @Test
    void buildReservoirTs_shouldWriteArrowWithoutDateAndPreserveSeriesHeaders() throws IOException {
        Path reservoirFile = createReservoirLevelsFile(
                "reservoir_trajectory", new String[]{"dAtE", "mINimum", "Moyenne", "MAXIMUM"});
        when(timeSeriesReader.readFromXlsx(reservoirFile, "2027", true))
                .thenReturn(new TimeSeriesReader().readFromXlsx(reservoirFile, "2027", true));
        TrajectoryEntity reservoirTrajectory = TrajectoryEntity.builder()
                .fileName("reservoir_trajectory")
                .horizon("2026-2027")
                .checksum("checksum123")
                .build();

        Map<String, String> result = hydroMeToJsonService.buildReservoirTs(reservoirTrajectory);

        assertThat(result).containsExactly(Map.entry(
                "node_a",
                "node_a_reservoir_levels_checksum123.arrow"));
        ArgumentCaptor<TimeSeriesMatrix> matrixCaptor = ArgumentCaptor.forClass(TimeSeriesMatrix.class);
        verify(nasFileService).saveMatrixToNasWithName(
                matrixCaptor.capture(),
                eq("node_a_reservoir_levels_checksum123.arrow"),
                eq("output/hydro_ME"));
        TimeSeriesMatrix matrix = matrixCaptor.getValue();
        assertThat(matrix.columns()).extracting(TimeSeriesMatrixColumn::name)
                .containsExactly("mINimum", "Moyenne", "MAXIMUM");
        assertThat(matrix.getRowCount()).isEqualTo(2);
        assertThat(matrix.columns().get(0).values()).containsExactly(1.25, 4.25);
        assertThat(matrix.columns().get(1).values()).containsExactly(2.5, 5.5);
        assertThat(matrix.columns().get(2).values()).containsExactly(3.75, 6.75);
    }

    @Test
    void buildHydroMeMap_withReservoirLevels_shouldAttachArrowToMatchingNode() throws IOException {
        Path reservoirFile = createReservoirLevelsFile(
                "reservoir_trajectory", new String[]{"Date", "Minimum", "Moyenne", "Maximum"});
        when(timeSeriesReader.readFromXlsx(reservoirFile, "2027", true))
                .thenReturn(new TimeSeriesReader().readFromXlsx(reservoirFile, "2027", true));
        when(hydroCapacityMeRepository.findByTrajectoryIdOrderByNodeAsc(1)).thenReturn(List.of(
                HydroCapacityMeEntity.builder().node("NODE_A").build()));
        TrajectoryEntity reservoirTrajectory = TrajectoryEntity.builder()
                .fileName("reservoir_trajectory")
                .horizon("2026-2027")
                .checksum("checksum123")
                .build();

        Map<String, Object> result =
                hydroMeToJsonService.buildHydroMeMap(trajectory, null, reservoirTrajectory);

        @SuppressWarnings("unchecked")
        Map<String, Object> node = (Map<String, Object>) result.get("NODE_A");
        assertThat(node).containsEntry(
                "reservoir_ts", "node_a_reservoir_levels_checksum123.arrow");
    }

    @Test
    void buildHydroMeMap_withHydroTimeSeries_shouldConvertColumnAAndAttachBothArrows() throws IOException {
        Path rorFile = createHydroTimeSeriesFile("timeseries_trajectory", "node_a", "ror");
        Path modFile = createHydroTimeSeriesFile("timeseries_trajectory", "node_a", "mod");
        when(timeSeriesReader.readFromXlsx(rorFile, "2027", false))
                .thenReturn(new TimeSeriesReader().readFromXlsx(rorFile, "2027", false));
        when(timeSeriesReader.readFromXlsx(modFile, "2027", false))
                .thenReturn(new TimeSeriesReader().readFromXlsx(modFile, "2027", false));
        when(hydroCapacityMeRepository.findByTrajectoryIdOrderByNodeAsc(1)).thenReturn(List.of(
                HydroCapacityMeEntity.builder().node("Node_A").build()));
        TrajectoryEntity timeSeriesTrajectory = TrajectoryEntity.builder()
                .fileName("timeseries_trajectory")
                .horizon("2026-2027")
                .checksum("checksum456")
                .build();

        Map<String, Object> result =
                hydroMeToJsonService.buildHydroMeMap(trajectory, null, null, timeSeriesTrajectory);

        @SuppressWarnings("unchecked")
        Map<String, Object> node = (Map<String, Object>) result.get("Node_A");
        assertThat(node.get("timeseries_ts")).isEqualTo(Map.of(
                "ror", "node_a_ror.checksum456.arrow",
                "mod", "node_a_mod.checksum456.arrow"));
        ArgumentCaptor<TimeSeriesMatrix> matrixCaptor = ArgumentCaptor.forClass(TimeSeriesMatrix.class);
        verify(nasFileService, times(2)).saveMatrixToNasWithName(
                matrixCaptor.capture(), any(), eq("output/hydro_ME"));
        assertThat(matrixCaptor.getAllValues()).extracting(matrix -> matrix.columns().get(0).name())
                .containsExactly("ror", "mod");
        assertThat(matrixCaptor.getAllValues().get(0).columns().get(0).values()).containsExactly(10.5, 11.5);
        assertThat(matrixCaptor.getAllValues().get(1).columns().get(0).values()).containsExactly(20.5, 21.5);
    }

    @Test
    void buildHydroMeMap_missingHydroTimeSeriesHorizon_shouldIncludeNodeInError() throws IOException {
        Path rorFile = createHydroTimeSeriesFile("timeseries_trajectory", "node_a", "ror");
        createHydroTimeSeriesFile("timeseries_trajectory", "node_a", "mod");
        when(hydroCapacityMeRepository.findByTrajectoryIdOrderByNodeAsc(1)).thenReturn(List.of(
                HydroCapacityMeEntity.builder().node("node_a").build()));
        when(timeSeriesReader.readFromXlsx(rorFile, "2027", false)).thenThrow(
                BusinessException.builder()
                        .message("Horizon {0} does not exist in file: {1}")
                        .errorMessageArguments(List.of("2027", "ror.xlsx"))
                        .build());
        TrajectoryEntity timeSeriesTrajectory = TrajectoryEntity.builder()
                .fileName("timeseries_trajectory")
                .horizon("2026-2027")
                .checksum("checksum456")
                .build();

        assertThatThrownBy(() ->
                hydroMeToJsonService.buildHydroMeMap(trajectory, null, null, timeSeriesTrajectory))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorMessageArguments())
                            .contains("node_a", "Horizon 2027 does not exist in file: ror.xlsx");
                });
    }

    @Test
    void buildHydroMeMap_withWaterValues_shouldConvertAllColumnsAndAttachArrowToNode() throws IOException {
        Path waterValuesFile = createWaterValuesFile("water_trajectory", "node_a");
        when(timeSeriesReader.readFromXlsx(waterValuesFile, "2027", false))
                .thenReturn(new TimeSeriesReader().readFromXlsx(waterValuesFile, "2027", false));
        when(hydroCapacityMeRepository.findByTrajectoryIdOrderByNodeAsc(1)).thenReturn(List.of(
                HydroCapacityMeEntity.builder().node("Node_A").build()));
        TrajectoryEntity waterTrajectory = TrajectoryEntity.builder()
                .fileName("water_trajectory")
                .horizon("2026-2027")
                .checksum("watercheck")
                .build();

        Map<String, Object> result =
                hydroMeToJsonService.buildHydroMeMap(trajectory, null, null, null, waterTrajectory);

        @SuppressWarnings("unchecked")
        Map<String, Object> node = (Map<String, Object>) result.get("Node_A");
        assertThat(node).containsEntry("water_values_ts", "Node_A_watercheck.arrow");
        ArgumentCaptor<TimeSeriesMatrix> matrixCaptor = ArgumentCaptor.forClass(TimeSeriesMatrix.class);
        verify(nasFileService).saveMatrixToNasWithName(
                matrixCaptor.capture(), eq("Node_A_watercheck.arrow"), eq("output/hydro_ME"));
        assertThat(matrixCaptor.getValue().columns()).extracting(TimeSeriesMatrixColumn::name)
                .containsExactly("Column0", "Column1");
        assertThat(matrixCaptor.getValue().columns().get(0).values()).containsExactly(10.5, 11.5);
        assertThat(matrixCaptor.getValue().columns().get(1).values()).containsExactly(20.5, 21.5);
    }

    @Test
    void buildReservoirTs_invalidHeaders_shouldThrowBusinessException() throws IOException {
        Path reservoirFile = createReservoirLevelsFile(
                "reservoir_trajectory", new String[]{"Date", "Moyenne", "Minimum", "Maximum"});
        when(timeSeriesReader.readFromXlsx(reservoirFile, "2027", true))
                .thenReturn(new TimeSeriesReader().readFromXlsx(reservoirFile, "2027", true));
        TrajectoryEntity reservoirTrajectory = TrajectoryEntity.builder()
                .fileName("reservoir_trajectory")
                .horizon("2026-2027")
                .checksum("checksum123")
                .build();

        assertThatThrownBy(() -> hydroMeToJsonService.buildReservoirTs(reservoirTrajectory))
                .isInstanceOf(BusinessException.class);
        verify(nasFileService, never()).saveMatrixToNasWithName(any(), any(), any());
    }

    @Test
    void buildHydroMeMap_dailyTimesteps_shouldGenerateArrowAndAddArrowFileName() throws IOException {
        when(hydroCapacityMeRepository.findByTrajectoryIdOrderByNodeAsc(1)).thenReturn(List.of(
                HydroCapacityMeEntity.builder()
                        .node("node_b")
                        .reservoirCapacity(BigDecimal.TEN)
                        .generatingPmaxTimestep("daily")
                        .generatingPmax(BigDecimal.ONE)
                        .pumpingPmaxTimestep("DAILY")
                        .pumpingPmax(BigDecimal.ONE)
                        .build()));
        when(timeSeriesReader.readSelectedColumnsFromXlsx(generatingTsFile, "2027", Set.of("node_b")))
                .thenReturn(matrixOf("Node_B", 400));
        when(timeSeriesReader.readSelectedColumnsFromXlsx(pumpingTsFile, "2027", Set.of("node_b")))
                .thenReturn(matrixOf("node_b", 365));

        Map<String, Object> result = hydroMeToJsonService.buildHydroMeMap(trajectory);

        @SuppressWarnings("unchecked")
        Map<String, Object> node = (Map<String, Object>) result.get("node_b");
        assertThat(node).containsOnlyKeys("properties", "generating_series", "pumping_series")
                .containsEntry("generating_series", "generating_node_b.uuid.arrow")
                .containsEntry("pumping_series", "pumping_node_b.uuid.arrow");

        ArgumentCaptor<TimeSeriesMatrix> matrixCaptor = ArgumentCaptor.forClass(TimeSeriesMatrix.class);
        verify(nasFileService, times(2)).saveMatrixToNas(matrixCaptor.capture(), any(), eq("output/hydro_ME"));
        assertThat(matrixCaptor.getAllValues()).allSatisfy(m -> {
            assertThat(m.columns()).hasSize(1);
            assertThat(m.columns().get(0).name()).isEqualTo("node_b");
            assertThat(m.columns().get(0).values()).hasSize(365);
        });
        verify(nasFileService).saveMatrixToNas(any(), eq("generating_node_b"), eq("output/hydro_ME"));
        verify(nasFileService).saveMatrixToNas(any(), eq("pumping_node_b"), eq("output/hydro_ME"));
    }

    @Test
    void buildHydroMeMap_multipleDailyNodes_shouldReadEachSeriesFileOnce() throws IOException {
        when(hydroCapacityMeRepository.findByTrajectoryIdOrderByNodeAsc(1)).thenReturn(List.of(
                HydroCapacityMeEntity.builder().node("node_a")
                        .generatingPmaxTimestep("daily").pumpingPmaxTimestep("daily").build(),
                HydroCapacityMeEntity.builder().node("node_b")
                        .generatingPmaxTimestep("daily").pumpingPmaxTimestep("daily").build()));
        when(timeSeriesReader.readSelectedColumnsFromXlsx(generatingTsFile, "2027", Set.of("node_a", "node_b")))
                .thenReturn(new TimeSeriesMatrix(List.of(
                        new TimeSeriesMatrixColumn("node_a", new double[365]),
                        new TimeSeriesMatrixColumn("node_b", new double[365]))));
        when(timeSeriesReader.readSelectedColumnsFromXlsx(pumpingTsFile, "2027", Set.of("node_a", "node_b")))
                .thenReturn(new TimeSeriesMatrix(List.of(
                        new TimeSeriesMatrixColumn("node_a", new double[365]),
                        new TimeSeriesMatrixColumn("node_b", new double[365]))));

        Map<String, Object> result = hydroMeToJsonService.buildHydroMeMap(trajectory);

        assertThat(result).hasSize(2);
        verify(timeSeriesReader, times(1))
                .readSelectedColumnsFromXlsx(generatingTsFile, "2027", Set.of("node_a", "node_b"));
        verify(timeSeriesReader, times(1))
                .readSelectedColumnsFromXlsx(pumpingTsFile, "2027", Set.of("node_a", "node_b"));
        verify(nasFileService, times(4)).saveMatrixToNas(any(), any(), eq("output/hydro_ME"));
    }

    @Test
    void buildHydroMeMap_dailyTimesteps_nodeNotInHeaders_shouldThrowBusinessException() throws IOException {
        when(hydroCapacityMeRepository.findByTrajectoryIdOrderByNodeAsc(1)).thenReturn(List.of(
                HydroCapacityMeEntity.builder()
                        .node("node_x")
                        .generatingPmaxTimestep("daily")
                        .build()));
        when(timeSeriesReader.readSelectedColumnsFromXlsx(generatingTsFile, "2027", Set.of("node_x")))
                .thenReturn(new TimeSeriesMatrix(List.of()));

        assertThatThrownBy(() -> hydroMeToJsonService.buildHydroMeMap(trajectory))
                .isInstanceOf(BusinessException.class);
        verify(nasFileService, never()).saveMatrixToNas(any(), any(), any());
    }

    @Test
    void buildHydroMeMap_dailyGenerating_missingFile_shouldThrowBusinessException() throws IOException {
        Files.delete(generatingTsFile);
        when(hydroCapacityMeRepository.findByTrajectoryIdOrderByNodeAsc(1)).thenReturn(List.of(
                HydroCapacityMeEntity.builder()
                        .node("node_x")
                        .generatingPmaxTimestep("daily")
                        .pumpingPmaxTimestep("annual")
                        .build()));

        assertThatThrownBy(() -> hydroMeToJsonService.buildHydroMeMap(trajectory))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(timeSeriesReader, nasFileService);
    }

    @Test
    void buildHydroMeMap_dailyPumping_missingFile_shouldThrowBusinessException() throws IOException {
        Files.delete(pumpingTsFile);
        when(hydroCapacityMeRepository.findByTrajectoryIdOrderByNodeAsc(1)).thenReturn(List.of(
                HydroCapacityMeEntity.builder()
                        .node("node_x")
                        .pumpingPmaxTimestep("daily")
                        .build()));

        assertThatThrownBy(() -> hydroMeToJsonService.buildHydroMeMap(trajectory))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void buildHydroMeMap_annualOnly_missingDailyFiles_shouldNotThrow() throws IOException {
        Files.delete(generatingTsFile);
        Files.delete(pumpingTsFile);
        when(hydroCapacityMeRepository.findByTrajectoryIdOrderByNodeAsc(1)).thenReturn(List.of(
                HydroCapacityMeEntity.builder()
                        .node("node_x")
                        .generatingPmaxTimestep("annual")
                        .pumpingPmaxTimestep("annual")
                        .build()));

        assertThat(hydroMeToJsonService.buildHydroMeMap(trajectory)).containsKey("node_x");
    }

    @Test
    void buildHydroMeMap_dailyTimesteps_readError_shouldThrowTechnicalException() throws IOException {
        when(hydroCapacityMeRepository.findByTrajectoryIdOrderByNodeAsc(1)).thenReturn(List.of(
                HydroCapacityMeEntity.builder()
                        .node("node_x")
                        .pumpingPmaxTimestep("daily")
                        .build()));
        when(timeSeriesReader.readSelectedColumnsFromXlsx(pumpingTsFile, "2027", Set.of("node_x")))
                .thenThrow(new IOException("boom"));

        assertThatThrownBy(() -> hydroMeToJsonService.buildHydroMeMap(trajectory))
                .isInstanceOf(TechnicalException.class);
    }

    @Test
    void buildHydroMeMap_mixedTimesteps_shouldAddPmaxAndSeries() throws IOException {
        when(hydroCapacityMeRepository.findByTrajectoryIdOrderByNodeAsc(1)).thenReturn(List.of(
                HydroCapacityMeEntity.builder()
                        .node("node_c")
                        .generatingPmaxTimestep("daily")
                        .pumpingPmaxTimestep("annual")
                        .pumpingPmax(BigDecimal.valueOf(30))
                        .hoursAtPumpingPmax(BigDecimal.valueOf(6))
                        .build()));
        when(timeSeriesReader.readSelectedColumnsFromXlsx(generatingTsFile, "2027", Set.of("node_c")))
                .thenReturn(matrixOf("node_c", 365));

        Map<String, Object> result = hydroMeToJsonService.buildHydroMeMap(trajectory);

        @SuppressWarnings("unchecked")
        Map<String, Object> node = (Map<String, Object>) result.get("node_c");
        assertThat(node).containsOnlyKeys("properties", "generating_series", "pumping_pmax");
    }

    @Test
    void buildHydroMeMap_dailyGeneratingAndHourlyPumping_shouldAddSeriesAndPmax() throws IOException {
        when(hydroCapacityMeRepository.findByTrajectoryIdOrderByNodeAsc(1)).thenReturn(List.of(
                HydroCapacityMeEntity.builder()
                        .node("node_d")
                        .generatingPmaxTimestep("daily")
                        .pumpingPmaxTimestep("hourly")
                        .pumpingPmax(BigDecimal.valueOf(40))
                        .hoursAtPumpingPmax(BigDecimal.valueOf(8))
                        .build()));
        when(timeSeriesReader.readSelectedColumnsFromXlsx(generatingTsFile, "2027", Set.of("node_d")))
                .thenReturn(matrixOf("node_d", 365));

        Map<String, Object> result = hydroMeToJsonService.buildHydroMeMap(trajectory);

        @SuppressWarnings("unchecked")
        Map<String, Object> node = (Map<String, Object>) result.get("node_d");
        assertThat(node).containsOnlyKeys("properties", "generating_series", "pumping_pmax")
                .containsEntry("generating_series", "generating_node_d.uuid.arrow")
                .containsEntry("pumping_pmax", Map.of("pmax", BigDecimal.valueOf(40), "hours", BigDecimal.valueOf(8)));
        verify(timeSeriesReader).readSelectedColumnsFromXlsx(generatingTsFile, "2027", Set.of("node_d"));
        verify(timeSeriesReader, never()).readSelectedColumnsFromXlsx(eq(pumpingTsFile), any(), any());
    }

    @Test
    void buildHydroMeMap_hourlyGeneratingAndDailyPumping_shouldAddPmaxAndSeries() throws IOException {
        when(hydroCapacityMeRepository.findByTrajectoryIdOrderByNodeAsc(1)).thenReturn(List.of(
                HydroCapacityMeEntity.builder()
                        .node("node_e")
                        .generatingPmaxTimestep("hourly")
                        .generatingPmax(BigDecimal.valueOf(90))
                        .hoursAtGeneratingPmax(BigDecimal.valueOf(18))
                        .pumpingPmaxTimestep("daily")
                        .build()));
        when(timeSeriesReader.readSelectedColumnsFromXlsx(pumpingTsFile, "2027", Set.of("node_e")))
                .thenReturn(matrixOf("node_e", 365));

        Map<String, Object> result = hydroMeToJsonService.buildHydroMeMap(trajectory);

        @SuppressWarnings("unchecked")
        Map<String, Object> node = (Map<String, Object>) result.get("node_e");
        assertThat(node).containsOnlyKeys("properties", "generating_pmax", "pumping_series")
                .containsEntry("generating_pmax", Map.of("pmax", BigDecimal.valueOf(90), "hours", BigDecimal.valueOf(18)))
                .containsEntry("pumping_series", "pumping_node_e.uuid.arrow");
        verify(timeSeriesReader).readSelectedColumnsFromXlsx(pumpingTsFile, "2027", Set.of("node_e"));
        verify(timeSeriesReader, never()).readSelectedColumnsFromXlsx(eq(generatingTsFile), any(), any());
    }

    @Test
    void buildHydroMeMap_noData_shouldReturnEmpty() {
        when(hydroCapacityMeRepository.findByTrajectoryIdOrderByNodeAsc(1)).thenReturn(Collections.emptyList());

        assertThat(hydroMeToJsonService.buildHydroMeMap(trajectory)).isEmpty();
    }

    @Test
    void buildHydroMeMap_nullTrajectory_shouldReturnEmpty() {
        assertThat(hydroMeToJsonService.buildHydroMeMap(null, null)).isEmpty();
        verifyNoInteractions(hydroCapacityMeRepository, hydroParametersMeRepository, hydroAllocationMeRepository);
    }

    @Test
    void buildHydroMeMap_withParameters_shouldMergePropertiesInflowStructureAndAllocation() throws IOException {
        TrajectoryEntity parametersTrajectory = TrajectoryEntity.builder().id(2).fileName("hydro_param_me").build();
        when(hydroCapacityMeRepository.findByTrajectoryIdOrderByNodeAsc(1)).thenReturn(List.of(
                HydroCapacityMeEntity.builder()
                        .node("Node_A")
                        .reservoirCapacity(BigDecimal.valueOf(5376))
                        .generatingPmaxTimestep("annual")
                        .generatingPmax(BigDecimal.valueOf(100))
                        .hoursAtGeneratingPmax(BigDecimal.valueOf(24))
                        .pumpingPmaxTimestep("daily")
                        .build()));
        when(timeSeriesReader.readSelectedColumnsFromXlsx(pumpingTsFile, "2027", Set.of("Node_A")))
                .thenReturn(matrixOf("node_a", 365));
        when(hydroParametersMeRepository.findByTrajectoryId(2)).thenReturn(List.of(
                HydroParametersMeEntity.builder()
                        .node("node_a")
                        .interMonthlyCorrelation(BigDecimal.valueOf(0.5))
                        .interDailyBreakdown(BigDecimal.ONE)
                        .intraDailyModulation(BigDecimal.valueOf(24))
                        .interMonthlyBreakdown(BigDecimal.ONE)
                        .initializeReservoirDate(0)
                        .leewayLow(BigDecimal.ONE)
                        .leewayUp(BigDecimal.ONE)
                        .pumpingEfficiency(BigDecimal.valueOf(0.75))
                        .reservoirManagement(true)
                        .useWater(false)
                        .useHeuristic(true)
                        .hardBounds(false)
                        .powerToLevel(false)
                        .useLeeway(false)
                        .followLoad(true)
                        .build()));
        when(hydroAllocationMeRepository.findByTrajectoryId(2)).thenReturn(List.of(
                HydroAllocationMeEntity.builder().node("NODE_A").area("AT").allocationCoefficient(BigDecimal.ZERO).build(),
                HydroAllocationMeEntity.builder().node("node_a").area("BE").allocationCoefficient(BigDecimal.ONE).build(),
                HydroAllocationMeEntity.builder().node("other").area("FR").allocationCoefficient(BigDecimal.ONE).build()));

        Map<String, Object> result = hydroMeToJsonService.buildHydroMeMap(trajectory, parametersTrajectory);

        assertThat(result).containsOnlyKeys("Node_A");
        @SuppressWarnings("unchecked")
        Map<String, Object> node = (Map<String, Object>) result.get("Node_A");
        assertThat(node).containsExactly(
                Map.entry("properties", node.get("properties")),
                Map.entry("inflow_structure", Map.of("intermonthly_correlation", BigDecimal.valueOf(0.5))),
                Map.entry("generating_pmax", Map.of("pmax", BigDecimal.valueOf(100), "hours", BigDecimal.valueOf(24))),
                Map.entry("pumping_series", "pumping_Node_A.uuid.arrow"),
                Map.entry("allocation", Map.of("AT", BigDecimal.ZERO, "BE", BigDecimal.ONE)));

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) node.get("properties");
        assertThat(properties.keySet()).containsExactly(
                "reservoir_capacity", "reservoir", "inter_daily_breakdown", "intra_daily_modulation",
                "inter_monthly_breakdown", "initialize_reservoir_date", "leeway_low", "leeway_up",
                "pumping_efficiency", "use_water", "use_heuristic", "hard_bounds", "power_to_level",
                "use_leeway", "follow_load");
        assertThat(properties)
                .containsEntry("reservoir_capacity", BigDecimal.valueOf(5376))
                .containsEntry("reservoir", true)
                .containsEntry("pumping_efficiency", BigDecimal.valueOf(0.75))
                .containsEntry("follow_load", true);
    }

    @Test
    void buildHydroMeMap_withParametersButNoAllocation_shouldNotAddAllocation() {
        TrajectoryEntity parametersTrajectory = TrajectoryEntity.builder().id(2).build();
        when(hydroCapacityMeRepository.findByTrajectoryIdOrderByNodeAsc(1)).thenReturn(List.of(
                HydroCapacityMeEntity.builder().node("node_a").build()));
        when(hydroParametersMeRepository.findByTrajectoryId(2)).thenReturn(List.of(
                HydroParametersMeEntity.builder().node("node_a").build()));
        when(hydroAllocationMeRepository.findByTrajectoryId(2)).thenReturn(Collections.emptyList());

        Map<String, Object> result = hydroMeToJsonService.buildHydroMeMap(trajectory, parametersTrajectory);

        @SuppressWarnings("unchecked")
        Map<String, Object> node = (Map<String, Object>) result.get("node_a");
        assertThat(node).containsKeys("properties", "inflow_structure").doesNotContainKey("allocation");
    }
}
