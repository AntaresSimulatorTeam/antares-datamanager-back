package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalControlsServiceImpl;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThermalControlsServiceImplTest {
    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private StudyRepository studyRepository;

    @InjectMocks
    private ThermalControlsServiceImpl thermalControlsService;

    // -------------------- Helper to create temp Excel files --------------------
    private Path createWorkbook(java.util.function.Consumer<Workbook> builder) throws IOException {
        Workbook wb = new XSSFWorkbook();
        builder.accept(wb);
        Path tmp = Files.createTempFile("costs_trajectory-", ".xlsx");
        try (FileOutputStream fos = new FileOutputStream(tmp.toFile())) {
            wb.write(fos);
        }
        wb.close();
        return tmp;
    }

    @Test
    void checkMissingClusters_shouldNotThrowExceptionWhenAllClustersArePresent() {
        Integer studyId = 1;
        String horizon = "2023-2024";
        Set<String> paramClusters = Set.of("ClusterA", "ClusterB");
        Set<String> installedPowerClusters = Set.of("ClusterA", "ClusterB");

        when(trajectoryRepository.findAllByStudyIdAndHorizonAndTypeOrderByVersionDesc(studyId, horizon, TrajectoryType.THERMAL_CAPACITY.name()))
                .thenReturn(List.of(
                        TrajectoryEntity.builder()
                                .thermalClusterCapacities(installedPowerClusters.stream()
                                        .map(cluster -> ThermalClusterCapacityEntity.builder().thermalClusterRef(ThermalClusterRef.builder().name(cluster).build()).build())
                                        .toList())
                                .build()
                ));

        assertDoesNotThrow(() -> thermalControlsService.checkMissingClusters(studyId, horizon, paramClusters, TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER, null));
    }

    @Test
    void checkMissingClusters_shouldThrowExceptionWhenClustersAreMissing() {
        Integer studyId = 1;
        String horizon = "2023-2024";
        Set<String> paramClusters = Set.of("ClusterA/FR");
        Set<String> installedPowerClusters = Set.of("ClusterA", "ClusterB");

        when(trajectoryRepository.findAllByStudyIdAndHorizonAndTypeOrderByVersionDesc(studyId, horizon, TrajectoryType.THERMAL_CAPACITY.name()))
                .thenReturn(List.of(
                        TrajectoryEntity.builder()
                                .thermalClusterCapacities(installedPowerClusters.stream()
                                        .map(cluster -> ThermalClusterCapacityEntity.builder().area("FR").thermalClusterRef(ThermalClusterRef.builder().name(cluster).build()).build())
                                        .toList())
                                .build()
                ));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                thermalControlsService.checkMissingClusters(studyId, horizon, paramClusters, TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER, "FR"));

        assertTrue(exception.getMessage().contains("Clusters : ClusterB/FR are not in Specific trajectory"));
    }

    @Test
    void checkMissingClusters_shouldNotThrowExceptionWhenNoInstalledPowerClustersExist() {
        Integer studyId = 1;
        String horizon = "2023-2024";
        Set<String> paramClusters = Set.of("ClusterA", "ClusterB");

        when(trajectoryRepository.findAllByStudyIdAndHorizonAndTypeOrderByVersionDesc(studyId, horizon, TrajectoryType.THERMAL_CAPACITY.name()))
                .thenReturn(List.of());

        assertDoesNotThrow(() -> thermalControlsService.checkMissingClusters(studyId, horizon, paramClusters, TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER, null));
    }


    @Test
    void verifyClustersInCommonParamTrajectory_shouldThrowExceptionWhenClustersAreMissing() {
        Integer studyId = 1;
        String horizon = "2025-2026";
        List<ThermalClusterCapacityEntity> capacities = List.of(
                ThermalClusterCapacityEntity.builder()
                        .thermalClusterRef(ThermalClusterRef.builder().name("ClusterA").build())
                        .build()
        );

        TrajectoryEntity commonParamTrajectory = TrajectoryEntity.builder()
                .thermalCommonParameters(List.of(
                        ThermalCommonParameterEntity.builder()
                                .thermalClusterRef(ThermalClusterRef.builder().name("ClusterB").build())
                                .build()
                ))
                .fileName("CommonParamFile")
                .build();

        when(trajectoryRepository.findByTypeAndStudyId(any(), any()))
                .thenReturn(List.of(commonParamTrajectory));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                thermalControlsService.verifyClustersInCommonParamTrajectory(studyId, horizon, capacities)
        );

        assertTrue(exception.getMessage().contains("Clusters ClusterA are not in Common trajectory CommonParamFile"));
    }

    // -------------------- Tests for verifyCostsTrajectory --------------------
    @Test
    void verifyCostsTrajectory_shouldThrowWhenBothSheetsMissing() throws IOException {
        Path file = createWorkbook(wb -> {
            // No 'costs' or 'rate' sheets
            wb.createSheet("other");
        });
        BusinessException ex = assertThrows(BusinessException.class, () ->
                thermalControlsService.verifyCostsTrajectory("2025-2026", file, "costs_testTrajectory", 1));
        assertTrue(ex.getMessage().contains("Missing costs/rate data in trajectory"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    void verifyCostsTrajectory_shouldThrowWhenCostsSheetMissing() throws IOException {
        Path file = createWorkbook(wb -> {
            wb.createSheet(ThermalControlsServiceImpl.SHEET_RATE);
        });
        BusinessException ex = assertThrows(BusinessException.class, () ->
                thermalControlsService.verifyCostsTrajectory("2025-2026", file, "costs_testTrajectory", 1));
        assertTrue(ex.getMessage().contains("Missing costs data in trajectory"));
        assertTrue(ex.getErrorMessageArguments().contains("costs_testTrajectory"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    void verifyCostsTrajectory_shouldThrowWhenRateSheetMissing() throws IOException {
        Path file = createWorkbook(wb -> {
            Sheet costs = wb.createSheet(ThermalControlsServiceImpl.SHEET_COSTS);
            // Minimal header to avoid earlier checks (won't be reached)
            Row h = costs.createRow(0);
            h.createCell(5).setCellValue("2025-2026");
            costs.createRow(1).createCell(5).setCellValue(1.0);
        });
        BusinessException ex = assertThrows(BusinessException.class, () ->
                thermalControlsService.verifyCostsTrajectory("2025-2026", file, "costs_testTrajectory", 1));
        assertTrue(ex.getMessage().contains("Missing rate data in trajectory"));
        assertTrue(ex.getErrorMessageArguments().contains("costs_testTrajectory"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    void verifyCostsTrajectory_shouldThrowWhenCostsHasNoData() throws IOException {
        Path file = createWorkbook(wb -> {
            Sheet rate = wb.createSheet(ThermalControlsServiceImpl.SHEET_RATE);
            Row rateHeader = rate.createRow(0);
            rateHeader.createCell(0).setCellValue("Type");
            rateHeader.createCell(1).setCellValue("2025");
            rate.createRow(1).createCell(0).setCellValue("rate1");

            Sheet costs = wb.createSheet(ThermalControlsServiceImpl.SHEET_COSTS);
            Row header = costs.createRow(0);

            header.createCell(0).setCellValue("country");
            header.createCell(1).setCellValue("fuel");
            header.createCell(2).setCellValue("comment");
            header.createCell(3).setCellValue("unit");
            header.createCell(4).setCellValue("modulation");
            header.createCell(5).setCellValue("2025");

        });
        BusinessException ex = assertThrows(BusinessException.class, () ->
                thermalControlsService.verifyCostsTrajectory("2025", file, "costs_testTrajectory", 1));
        assertTrue(ex.getMessage().contains("No data for horizon {0} in THERMAL Costs trajectory {1} in costs tab"));
        assertTrue(ex.getErrorMessageArguments().contains("costs_testTrajectory"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    void verifyCostsTrajectory_shouldThrowWhenHorizonMissingInCostsHeader() throws IOException {
        Path file = createWorkbook(wb -> {
            Sheet costs = wb.createSheet(ThermalControlsServiceImpl.SHEET_COSTS);
            Row header = costs.createRow(0);
            header.createCell(5).setCellValue("OTHER"); // horizon not matching
            costs.createRow(1).createCell(5).setCellValue(1.0); // ensure lastRowNum >=1
            wb.createSheet(ThermalControlsServiceImpl.SHEET_RATE).createRow(0).createCell(0).setCellValue("H");
            wb.getSheet(ThermalControlsServiceImpl.SHEET_RATE).createRow(1).createCell(0).setCellValue("x");
        });
        BusinessException ex = assertThrows(BusinessException.class, () ->
                thermalControlsService.verifyCostsTrajectory("2025-2026", file, "costs_testTrajectory", 1));
        assertTrue(ex.getMessage().contains("Horizon does not exist in THERMAL Costs trajectory"));
        assertTrue(ex.getMessage().contains("costs tab"));
        assertTrue(ex.getErrorMessageArguments().contains("costs_testTrajectory"));
    }

    @Test
    void verifyCostsTrajectory_shouldThrowWhenNonNumericValueInCostsData() throws IOException {
        Path file = createWorkbook(wb -> {
            Sheet costs = wb.createSheet(ThermalControlsServiceImpl.SHEET_COSTS);
            Row header = costs.createRow(0);
            header.createCell(5).setCellValue("2025-2026");
            Row row = costs.createRow(1);
            row.createCell(5).setCellValue("abc"); // non-numeric
            Sheet rate = wb.createSheet(ThermalControlsServiceImpl.SHEET_RATE);
            rate.createRow(0).createCell(0).setCellValue("H");
            rate.createRow(1).createCell(0).setCellValue("x");
        });
        BusinessException ex = assertThrows(BusinessException.class, () ->
                thermalControlsService.verifyCostsTrajectory("2025-2026", file, "costs_testTrajectory", 1));
        assertTrue(ex.getMessage().contains("must be numeric"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    void verifyCostsTrajectory_shouldThrowWhenRateHasNoData() throws IOException {
        Path file = createWorkbook(wb -> {
            Sheet costs = wb.createSheet(ThermalControlsServiceImpl.SHEET_COSTS);
            Row header = costs.createRow(0);
            header.createCell(5).setCellValue("2025");
            costs.createRow(1).createCell(5).setCellValue(2.0);

            Sheet rate = wb.createSheet(ThermalControlsServiceImpl.SHEET_RATE);
            Row rateHeader = rate.createRow(0);
            rateHeader.createCell(0).setCellValue("Type");
            rateHeader.createCell(1).setCellValue("2025");
            // Pas de ligne de données ajoutée
        });

        BusinessException ex = assertThrows(BusinessException.class, () ->
                thermalControlsService.verifyCostsTrajectory("2025", file, "costs_testTrajectory", 1));
        assertTrue(ex.getMessage().contains("No data for horizon"));
        assertTrue(ex.getMessage().contains("rate tab"));
    }


    @Test
    void verifyCostsTrajectory_shouldThrowWhenRateHeaderInvalid() throws IOException {
        Path file = createWorkbook(wb -> {
            Sheet costs = wb.createSheet(ThermalControlsServiceImpl.SHEET_COSTS);
            Row header = costs.createRow(0);
            header.createCell(5).setCellValue("2025-2026");
            costs.createRow(1).createCell(5).setCellValue(2.0);
            Sheet rate = wb.createSheet(ThermalControlsServiceImpl.SHEET_RATE);
            rate.createRow(0).createCell(0).setCellValue("c1"); // only 1 cell -> lastCellNum<2
            rate.createRow(1).createCell(0).setCellValue("x");
        });
        BusinessException ex = assertThrows(BusinessException.class, () ->
                thermalControlsService.verifyCostsTrajectory("2025-2026", file, "CostTraj", 1));
        assertTrue(ex.getMessage().contains("Horizon does not exist in THERMAL Costs trajectory"));
        assertTrue(ex.getMessage().contains("rate tab"));
    }

    @Test
    void verifyClustersInCommonParamTrajectory_shouldNotThrowExceptionWhenAllClustersArePresent() {
        Integer studyId = 1;
        String horizon = "2025-2026";
        List<ThermalClusterCapacityEntity> capacities = List.of(
                ThermalClusterCapacityEntity.builder()
                        .thermalClusterRef(ThermalClusterRef.builder().name("ClusterA").build())
                        .build()
        );

        TrajectoryEntity commonParamTrajectory = TrajectoryEntity.builder()
                .thermalCommonParameters(List.of(
                        ThermalCommonParameterEntity.builder()
                                .thermalClusterRef(ThermalClusterRef.builder().name("ClusterA").build())
                                .build()
                ))
                .build();

        when(trajectoryRepository.findByTypeAndStudyId(any(), any()))
                .thenReturn(List.of(commonParamTrajectory));

        assertDoesNotThrow(() ->
                thermalControlsService.verifyClustersInCommonParamTrajectory(studyId, horizon, capacities)
        );
    }

    @Test
    void verifyClustersInCommonParamTrajectory_shouldNotThrowExceptionWhenNoCommonParamTrajectoryExists() {
        Integer studyId = 1;
        String horizon = "2025-2026";
        List<ThermalClusterCapacityEntity> capacities = List.of(
                ThermalClusterCapacityEntity.builder()
                        .thermalClusterRef(ThermalClusterRef.builder().name("ClusterA").build())
                        .build()
        );
        when(trajectoryRepository.findByTypeAndStudyId(any(), any()))
                .thenReturn(List.of());

        assertDoesNotThrow(() ->
                thermalControlsService.verifyClustersInCommonParamTrajectory(studyId, horizon, capacities)
        );
    }


    @org.junit.jupiter.api.Test
    void verifyClustersInSpecificParamTrajectory_shouldThrowExceptionWhenClustersAreMissing() {
        Integer studyId = 1;
        String horizon = "2025-2026";
        List<ThermalClusterCapacityEntity> capacities = List.of(
                ThermalClusterCapacityEntity.builder().area("FR")
                        .thermalClusterRef(ThermalClusterRef.builder().name("ClusterA").build())
                        .build()
        );

        TrajectoryEntity specificParamTrajectory = TrajectoryEntity.builder()
                .thermalSpecificParameters(List.of(
                        ThermalSpecificParametersEntity.builder()
                                .area("FR")
                                .thermalClusterRef(ThermalClusterRef.builder().name("ClusterB").build())
                                .build()
                ))
                .fileName("SpecificParamFile")
                .build();

        when(trajectoryRepository.findByTypeAndStudyId(any(), any()))
                .thenReturn(List.of(specificParamTrajectory));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                thermalControlsService.verifyClustersInSpecificParamTrajectory(studyId, horizon, capacities)
        );

        assertTrue(exception.getMessage().contains("Clusters ClusterA/FR are not in Specific trajectory SpecificParamFile"));
    }

    @org.junit.jupiter.api.Test
    void verifyClustersInSpecificParamTrajectory_shouldNotThrowExceptionWhenAllClustersArePresent() {
        Integer studyId = 1;
        String horizon = "2025-2026";
        List<ThermalClusterCapacityEntity> capacities = List.of(
                ThermalClusterCapacityEntity.builder()
                        .thermalClusterRef(ThermalClusterRef.builder().name("ClusterA").build())
                        .build()
        );

        TrajectoryEntity specificParamTrajectory = TrajectoryEntity.builder()
                .thermalSpecificParameters(List.of(
                        ThermalSpecificParametersEntity.builder()
                                .thermalClusterRef(ThermalClusterRef.builder().name("ClusterA").build())
                                .build()
                ))
                .build();

        when(trajectoryRepository.findByTypeAndStudyId(any(), any()))
                .thenReturn(List.of(specificParamTrajectory));

        assertDoesNotThrow(() ->
                thermalControlsService.verifyClustersInSpecificParamTrajectory(studyId, horizon, capacities)
        );
    }

    @org.junit.jupiter.api.Test
    void verifyClustersInSpecificParamTrajectory_shouldNotThrowExceptionWhenNoSpecificParamTrajectoryExists() {
        Integer studyId = 1;
        String horizon = "2025-2026";
        List<ThermalClusterCapacityEntity> capacities = List.of(
                ThermalClusterCapacityEntity.builder()
                        .thermalClusterRef(ThermalClusterRef.builder().name("ClusterA").build())
                        .build()
        );

        when(trajectoryRepository.findByTypeAndStudyId(any(), any()))
                .thenReturn(List.of());

        assertDoesNotThrow(() ->
                thermalControlsService.verifyClustersInSpecificParamTrajectory(studyId, horizon, capacities)
        );
    }

    @Test
    void validateDataCells_shouldThrowWhenAnyHeaderIsEmpty() throws IOException {
        Path file = createWorkbook(wb -> {
            Sheet costs = wb.createSheet(ThermalControlsServiceImpl.SHEET_COSTS);
            Row header = costs.createRow(0);

            // Column F (index 5) → empty header triggers exception
            header.createCell(5).setCellValue("");

            // Add data row to prevent row skipping
            Row dataRow = costs.createRow(1);
            dataRow.createCell(5).setCellValue(1.0); // under empty header
            dataRow.createCell(6).setCellValue(2.0); // under valid header
        });

        try (Workbook wb = WorkbookFactory.create(file.toFile())) {
            Sheet costsSheet = wb.getSheet(ThermalControlsServiceImpl.SHEET_COSTS);

            BusinessException ex = assertThrows(BusinessException.class, () ->
                    thermalControlsService.validateDataCells(
                            costsSheet,
                            "costs_testTrajectory",
                            ThermalControlsServiceImpl.SHEET_COSTS, 6));


        }
    }

    @Test
    void verifyThermalFuel_shouldNotThrowWhenAllTechnologiesExist() {
        Integer studyId = 1;
        String horizon = "2025-2026";
        String trajectoryName = "EconomicTrajectory";
        Set<String> listTechnology = Set.of("tech1", "tech2");
        TrajectoryType trajectoryType = TrajectoryType.THERMAL_ECONOMIC_COST_PARAMETER;

        when(studyRepository.findById(studyId)).thenReturn(Optional.of(
                StudyEntity.builder()
                        .trajectories(Set.of(
                                TrajectoryEntity.builder()
                                        .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                                        .horizon(horizon)
                                        .thermalCommonParameters(List.of(
                                                ThermalCommonParameterEntity.builder()
                                                        .fuel("tech1")
                                                        .thermalClusterRef(ThermalClusterRef.builder()
                                                                .name("Cluster1")
                                                                .thermalTechnology(ThermalTechnology.builder().name("tech1").build())
                                                                .build())
                                                        .build(),
                                                ThermalCommonParameterEntity.builder()
                                                        .fuel("tech2")
                                                        .thermalClusterRef(ThermalClusterRef.builder()
                                                                .name("Cluster2")
                                                                .thermalTechnology(ThermalTechnology.builder().name("tech2").build())
                                                                .build())
                                                        .build()
                                        ))
                                        .build()
                        ))
                        .build()
        ));

        assertDoesNotThrow(() -> thermalControlsService.verifyThermalFuel(studyId, horizon, trajectoryName, listTechnology, trajectoryType));
    }

    @Test
    void verifyThermalFuelDoesNotExist() {
        Integer studyId = 1;
        String horizon = "2025-2026";
        String trajectoryName = "EconomicTrajectory";
        Set<String> listTechnology = Set.of("tech2", "tech3");
        TrajectoryType trajectoryType = TrajectoryType.THERMAL_ECONOMIC_COST_PARAMETER;

        when(studyRepository.findById(studyId)).thenReturn(Optional.of(
                StudyEntity.builder()
                        .trajectories(Set.of(
                                TrajectoryEntity.builder()
                                        .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                                        .horizon(horizon)
                                        .thermalCommonParameters(List.of(
                                                ThermalCommonParameterEntity.builder()
                                                        .fuel("Tech1")
                                                        .thermalClusterRef(ThermalClusterRef.builder()
                                                                .name("Cluster1")
                                                                .thermalTechnology(ThermalTechnology.builder().name("tech1").build())
                                                                .build())
                                                        .build()
                                        ))
                                        .build(),
                                TrajectoryEntity.builder()
                                        .type(TrajectoryType.THERMAL_CAPACITY.name())
                                        .horizon(horizon)
                                        .thermalClusterCapacities(List.of(
                                                ThermalClusterCapacityEntity.builder()
                                                        .thermalClusterRef(ThermalClusterRef.builder()
                                                                .name("Cluster1")
                                                                .thermalTechnology(ThermalTechnology.builder().name("tech1").build())
                                                                .build())
                                                        .build()
                                        ))
                                        .build()
                        ))
                        .build()
        ));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                thermalControlsService.verifyThermalFuel(studyId, horizon, trajectoryName, listTechnology, trajectoryType));

        assertTrue(exception.getMessage().contains("Fuel {0} does not exist in Cost Trajectory {1} for horizon {2}"));
    }

    @Test
    void verifyThermalFuel_shouldThrowWhenStudyNotFound() {
        Integer studyId = 1;
        String horizon = "2025-2026";
        String trajectoryName = "EconomicTrajectory";
        Set<String> listTechnology = Set.of("tech1");
        TrajectoryType trajectoryType = TrajectoryType.THERMAL_ECONOMIC_COST_PARAMETER;

        when(studyRepository.findById(studyId)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () ->
                thermalControlsService.verifyThermalFuel(studyId, horizon, trajectoryName, listTechnology, trajectoryType));

        assertTrue(exception.getMessage().contains("Study with id {0} not found"));
    }


}