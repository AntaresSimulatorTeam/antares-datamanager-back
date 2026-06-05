package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalControlService;
import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalClusterRefServiceImpl;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalSpecificFileProcessorServiceImpl;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.rte_france.antares.datamanager_back.util.Utils.OTHERS_AREA;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ThermalSpecificFileProcessorServiceImplTest {

    private static final String HORIZON = "2025";
    private static final String TRAJECTORY_NAME = "thermal_specific_test";

    @TempDir
    Path tempDir;

    @Mock
    private ThermalSpecificParametersRepository thermalSpecificParametersRepository;

    @Mock
    private ThermalFileProcessorServiceImpl thermalFileProcessorService;

    @Mock
    private AreaRepository areaRepository;

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private UserService userService;

    @Mock
    private ThermalControlService thermalControlService;

    @Mock
    private ThermalClusterRefServiceImpl thermalClusterRefServiceImpl;

    @Mock
    private StudyRepository studyRepository;

    @Mock
    private WarningRepository warningRepository;


    @InjectMocks
    private ThermalSpecificFileProcessorServiceImpl service;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldThrowWhenHorizonSheetMissing() throws IOException {
        Path file = writeWorkbookToTemp(createWorkbookWithNoMatchingSheet());
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.buildThermalSpecificParameterValueList(TRAJECTORY_NAME, file, HORIZON, "FR", 1)
        );
        assertTrue(ex.getMessage().contains("Horizon " + HORIZON + " does not exist"));
    }

    @Test
    void shouldThrowWhenHeaderMissing() throws IOException {
        // Create workbook with the sheet but without header row (row 2 is null)
        var wb = new XSSFWorkbook();
        wb.createSheet(HORIZON).createRow(0); // row0 exists, row2 is missing
        Path file = writeWorkbookToTemp(wb);
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.buildThermalSpecificParameterValueList(TRAJECTORY_NAME, file, HORIZON, "FR", 1)
        );
        assertTrue(ex.getMessage().startsWith("Missing columns "));
    }

    @Test
    void shouldProcessValidRowsAndReturnEntities() throws IOException {
        when(areaRepository.findAllByStudyId(any())).thenReturn(List.of(AreaEntity.builder().id(1).name("FR").build(),AreaEntity.builder().id(1).name("DE").build()));
        when(thermalClusterRefServiceImpl.findOrCreateThermalClusterRef(any(), anyString(), anyString()))
                .thenReturn(ThermalClusterRef.builder().id(1).name("Cluster1").namePemmdb("PEM1").build());

        Path file = writeWorkbookToTemp(createValidWorkbook(2));

        List<ThermalSpecificParametersEntity> result = service.buildThermalSpecificParameterValueList(TRAJECTORY_NAME, file, HORIZON, OTHERS_AREA, 1);

        assertEquals(2, result.size());
        // Basic field checks
        assertEquals("FR", result.get(0).getNode());
        assertEquals("Cluster1", result.get(0).getThermalClusterRef().getName());
        assertEquals(1.0, result.get(0).getMinStableGeneration());
        assertEquals(38.0, result.get(0).getP12());
    }


    @Test
    void shouldThrowForOthersAreaIfNoStudyAreaPresent() throws IOException {
        when(thermalClusterRefServiceImpl.findOrCreateThermalClusterRef(any(), anyString(), anyString()))
                .thenReturn(ThermalClusterRef.builder().id(1).name("Cluster1").namePemmdb("PEM1").build());
        // Study has ES and IT, but rows contain FR and DE -> none present
        when(areaRepository.findAllByStudyId(anyInt())).thenReturn(List.of(
                AreaEntity.builder().id(1).name("ES").build(),
                AreaEntity.builder().id(2).name("IT").build()
        ));

        Path file = writeWorkbookToTemp(createValidWorkbook(2));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.buildThermalSpecificParameterValueList(TRAJECTORY_NAME, file, HORIZON, OTHERS_AREA, 42)
        );
        assertTrue(ex.getMessage().contains("None of the areas of trajectory AREA are present"));
    }

    @Test
    void shouldThrowWhenNumericColumnsContainText() throws IOException {
        when(areaRepository.findAllByStudyId(any())).thenReturn(List.of(AreaEntity.builder().id(1).name("FR").build()));
        when(thermalClusterRefServiceImpl.findOrCreateThermalClusterRef(any(), anyString(), anyString()))
                .thenReturn(ThermalClusterRef.builder().id(1).name("Cluster1").namePemmdb("PEM1").build());
        // Create wb with one row and inject a text in a numeric column (index 5)
        var wb = createValidWorkbook(1);
        var sheet = wb.getSheet(HORIZON);
        var row = sheet.getRow(3); // first data row (0-based index)
        row.getCell(5).setCellValue("NaN");

        Path file = writeWorkbookToTemp(wb);
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.buildThermalSpecificParameterValueList(TRAJECTORY_NAME, file, HORIZON, "FR", 1)
        );
        assertTrue(ex.getMessage().contains("are not numeric"));
    }

    @Test
    void shouldThrowWhenNumericColumnsContainNegativeValue() throws IOException {
        when(areaRepository.findAllByStudyId(any())).thenReturn(List.of(AreaEntity.builder().id(1).name("FR").build()));
        when(thermalClusterRefServiceImpl.findOrCreateThermalClusterRef(any(), anyString(), anyString()))
                .thenReturn(ThermalClusterRef.builder().id(1).name("Cluster1").namePemmdb("PEM1").build());
        // Create wb with one row and inject a negative number in a numeric column (index 5 -> min_stable_generation)
        var wb = createValidWorkbook(1);
        var sheet = wb.getSheet(HORIZON);
        var row = sheet.getRow(3); // first data row (0-based index)
        row.getCell(5).setCellValue(-1.0);

        Path file = writeWorkbookToTemp(wb);
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.buildThermalSpecificParameterValueList(TRAJECTORY_NAME, file, HORIZON, "FR", 1)
        );
        assertTrue(ex.getMessage().contains("must be positive"));
    }

    @Test
    void shouldThrowRegardlessOfSelectedAreaIfNoStudyAreaPresent() throws IOException {
        when(thermalClusterRefServiceImpl.findOrCreateThermalClusterRef(any(), anyString(), anyString()))
                .thenReturn(ThermalClusterRef.builder().id(1).name("Cluster1").namePemmdb("PEM1").build());
        // Study has ES and IT, but rows contain FR and DE -> none present
        when(areaRepository.findAllByStudyId(anyInt())).thenReturn(List.of(
                AreaEntity.builder().id(1).name("ES").build(),
                AreaEntity.builder().id(2).name("IT").build()
        ));

        Path file = writeWorkbookToTemp(createValidWorkbook(2));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.buildThermalSpecificParameterValueList(TRAJECTORY_NAME, file, HORIZON, "FR", 42)
        );
        assertTrue(ex.getMessage().contains("None of the areas of trajectory AREA are present"));
    }


    @Test
    void buildThermalSpecificParameterValueList_parsesRowCorrectly(@TempDir Path tempDir) throws Exception {
        String horizon = "2025-2026";
        Path file = tempDir.resolve("specific_param_test.xlsx");
        Files.write(file, generateSpecificParametersExcelFile(horizon));
        when(areaRepository.findAllByStudyId(any())).thenReturn(List.of(AreaEntity.builder().id(1).name("NODE-A").build()));

        // clusters must exist and be resolvable
        when(thermalClusterRefServiceImpl.findOrCreateThermalClusterRef(any(), anyString(), anyString()))
                .thenAnswer(inv -> ThermalClusterRef.builder()
                        .name(inv.getArgument(1))
                        .namePemmdb(inv.getArgument(2))
                        .build());

        List<ThermalSpecificParametersEntity> list = service.buildThermalSpecificParameterValueList("specific_param_BD", file, horizon, OTHERS_AREA, 1);
        assertEquals(1, list.size());
        ThermalSpecificParametersEntity e = list.get(0);

        assertEquals("NODE-A", e.getNode());
        assertNotNull(e.getThermalClusterRef());
        assertEquals("ClusterA", e.getThermalClusterRef().getName());
        assertEquals("PEM-001", e.getThermalClusterRef().getNamePemmdb());

        // Check a few numeric fields (rounded to 2 decimals by castDouble)
        assertEquals(10.00, e.getMinStableGeneration());
        assertEquals(1.50, e.getSpinning());
        assertEquals(0.42, e.getEfficiency());
        assertEquals(0.11, e.getF1());
        assertEquals(210.00, e.getP12());
    }

    @Test
    void buildThermalSpecificParameterValueList_missingSheet_throwsBusinessException(@TempDir Path tempDir) throws Exception {
        String horizon = "2025-2026";
        // Create workbook with a different sheet name
        Path file = tempDir.resolve("thermal_specific_parameters_test.xlsx");
        Files.write(file, generateSpecificParametersExcelFile("OTHER"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.buildThermalSpecificParameterValueList("specific_param_", file, horizon, "FR", 1)
        );
        assertTrue(ex.getMessage().contains("Horizon " + horizon + " does not exist in the THERMAL Specific Param trajectory"));
    }
    // ===================== Helpers =====================

    private static XSSFWorkbook createWorkbookWithNoMatchingSheet() {
        var wb = new XSSFWorkbook();
        wb.createSheet("OTHER");
        return wb;
    }

    private static XSSFWorkbook createValidWorkbook(int rows) {
        return createValidWorkbook(rows, false, true);
    }

    private static XSSFWorkbook createValidWorkbook(int rows, boolean makeClusterEmpty, boolean mockClusterExists) {
        var wb = new XSSFWorkbook();
        var sheet = wb.createSheet(HORIZON);

        // Ensure rows 0..2 exist with headers at row 2
        var hdr0 = sheet.createRow(0);
        var hdr = sheet.createRow(2);
        String[] headers = buildHeaders();
        for (int i = 0; i < headers.length; i++) {
            hdr.createCell(i).setCellValue(headers[i]);
        }

        for (int r = 0; r < rows; r++) {
            var row = sheet.createRow(3 + r); // data starts at row index 3 (4th row)
            // 0..2 textual (node, cluster_pemmdb, cluster)
            row.createCell(0).setCellValue(r == 0 ? "FR" : "DE");
            row.createCell(1).setCellValue("PEM1");
            if (makeClusterEmpty) {
                row.createCell(2).setCellValue("");
            } else {
                row.createCell(2).setCellValue("Cluster1");
            }

            // numeric columns 3..40
            int v = 1;
            for (int c = 3; c <= 40; c++) {
                row.createCell(c).setCellValue(v++);
            }
        }
        return wb;
    }

    private static String[] buildHeaders() {
        String[] base = new String[]{
                "node",
                "cluster_PEMMDB",
                "cluster",
                "min_stable_generation",
                "spinning",
                "efficiency",
                "FO_rate",
                "FO_duration",
                "PO_duration",
                "PO_winter",
                "marginal_cost",
                "market_bid",
                "MR_specific",
                "CM_specific",
                "NPO_max_winter",
                "NPO_max_summer",
                "nb_unit"
        };
        String[] headers = new String[41];
        System.arraycopy(base, 0, headers, 0, base.length);
        int idx = base.length;
        for (int i = 1; i <= 12; i++) headers[idx++] = "F" + i;
        for (int i = 1; i <= 12; i++) headers[idx++] = "P" + i;
        return headers;
    }

    private Path writeWorkbookToTemp(XSSFWorkbook wb) throws IOException {
        try (var baos = new ByteArrayOutputStream()) {
            wb.write(baos);
            byte[] bytes = baos.toByteArray();
            Path file = tempDir.resolve("thermal_specific_test.xlsx");
            Files.write(file, bytes);
            return file;
        }
    }
    private static byte[] generateSpecificParametersExcelFile(String horizonSheetName) throws IOException {
        try (var outputStream = new ByteArrayOutputStream();
             var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet(horizonSheetName);

            // Header row at index 0 with labels for columns used by castDouble error messages
            var header = sheet.createRow(0);
            String[] headerLabels = new String[41];
            headerLabels[0] = "Node";
            headerLabels[1] = "Cluster PEMMDB";
            headerLabels[2] = "Cluster Name";
            headerLabels[3] = "Min Stable Generation";
            headerLabels[4] = "Spinning";
            headerLabels[5] = "Efficiency";
            headerLabels[6] = "FO Rate";
            headerLabels[7] = "FO Duration";
            headerLabels[8] = "PO Duration";
            headerLabels[9] = "PO Winter";
            headerLabels[10] = "Marginal Cost";
            headerLabels[11] = "Market Bid";
            headerLabels[12] = "MR Specific";
            headerLabels[13] = "CM Specific";
            headerLabels[14] = "NPO Max Winter";
            headerLabels[15] = "NPO Max Summer";
            headerLabels[16] = "Nb Unit";
            for (int i = 17; i <= 28; i++) {
                headerLabels[i] = "F" + (i - 16);
            }
            for (int i = 29; i <= 40; i++) {
                headerLabels[i] = "P" + (i - 28);
            }
            for (int i = 0; i < headerLabels.length; i++) {
                if (headerLabels[i] == null) headerLabels[i] = "Col" + i;
                header.createCell(i).setCellValue(headerLabels[i]);
            }

            // Leave row 1 and 2 empty to mimic metadata rows; first data row is index 3
            var row = sheet.createRow(3);
            Object[] values = new Object[]{
                    "NODE-A", "PEM-001", "ClusterA",
                    10.0, 1.5, 0.42, 0.05, 2.0, 3.0, 4.0, 50.0, 60.0,
                    1.0, 0.0, 7.0, 8.0, 2.0,
                    0.11, 0.12, 0.13, 0.14, 0.15, 0.16, 0.17, 0.18, 0.19, 0.2, 0.21, 0.22,
                    100.0, 110.0, 120.0, 130.0, 140.0, 150.0, 160.0, 170.0, 180.0, 190.0, 200.0, 210.0
            };
            for (int c = 0; c < values.length; c++) {
                if (values[c] instanceof Number n) {
                    row.createCell(c).setCellValue(n.doubleValue());
                } else {
                    row.createCell(c).setCellValue(values[c].toString());
                }
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    @Test
    void shouldReturnEmptySetWhenNoPreferredEntitiesFound() {
        when(thermalSpecificParametersRepository.findPreferredEntitiesByStudyIdAndHorizon(anyInt(), anyString()))
                .thenReturn(Collections.emptyList());

        Set<String> result = service.getListClusterByAreaForSpecificParam("2025", 1, true);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnClustersForMrSpecificWhenMrIsTrue() {
        when(thermalSpecificParametersRepository.findPreferredEntitiesByStudyIdAndHorizon(anyInt(), anyString()))
                .thenReturn(List.of(
                        ThermalSpecificParametersEntity.builder()
                                .area("FR")
                                .mrSpecific(1)
                                .thermalClusterRef(ThermalClusterRef.builder().name("ClusterA").build())
                                .build(),
                        ThermalSpecificParametersEntity.builder()
                                .area("DE")
                                .mrSpecific(1)
                                .thermalClusterRef(ThermalClusterRef.builder().name("ClusterB").build())
                                .build()
                ));

        Set<String> result = service.getListClusterByAreaForSpecificParam("2025", 1, true);

        assertEquals(Set.of("fr_clustera", "de_clusterb"), result);
    }

    @Test
    void shouldReturnClustersForCmSpecificWhenMrIsFalse() {
        when(thermalSpecificParametersRepository.findPreferredEntitiesByStudyIdAndHorizon(anyInt(), anyString()))
                .thenReturn(List.of(
                        ThermalSpecificParametersEntity.builder()
                                .area("FR")
                                .cmSpecific(1)
                                .thermalClusterRef(ThermalClusterRef.builder().name("ClusterA").build())
                                .build(),
                        ThermalSpecificParametersEntity.builder()
                                .area("DE")
                                .cmSpecific(1)
                                .thermalClusterRef(ThermalClusterRef.builder().name("ClusterB").build())
                                .build()
                ));

        Set<String> result = service.getListClusterByAreaForSpecificParam("2025", 1, false);

        assertEquals(Set.of("fr_clustera", "de_clusterb"), result);
    }

    @Test
    void shouldIgnoreEntitiesWithNullOrZeroSpecificValues() {
        when(thermalSpecificParametersRepository.findPreferredEntitiesByStudyIdAndHorizon(anyInt(), anyString()))
                .thenReturn(List.of(
                        ThermalSpecificParametersEntity.builder()
                                .area("FR")
                                .mrSpecific(0)
                                .thermalClusterRef(ThermalClusterRef.builder().name("ClusterA").build())
                                .build(),
                        ThermalSpecificParametersEntity.builder()
                                .area("DE")
                                .mrSpecific(null)
                                .thermalClusterRef(ThermalClusterRef.builder().name("ClusterB").build())
                                .build()
                ));

        Set<String> result = service.getListClusterByAreaForSpecificParam("2025", 1, true);

        assertTrue(result.isEmpty());
    }

    @Test
    void testIsParamModulationRequired_returnsTrue_whenClustersContainMrOrCm() {
        // GIVEN
        Integer studyId = 1;
        String horizon = "H1";

        ThermalSpecificParametersEntity p1 = new ThermalSpecificParametersEntity();
        p1.setMrSpecific(1);
        p1.setCmSpecific(0);
        ThermalSpecificParametersEntity p2 = new ThermalSpecificParametersEntity();
        p2.setMrSpecific(0);
        p2.setCmSpecific(0);

        when(thermalSpecificParametersRepository.findPreferredEntitiesByStudyIdAndHorizon(studyId, horizon))
                .thenReturn(List.of(p1,p2));

        // WHEN
        boolean result = service.isParamModulationRequired(horizon, studyId);

        // THEN
        assertTrue(result);

    }

}
