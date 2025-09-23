package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalClusterRef;
import com.rte_france.antares.datamanager_back.repository.model.ThermalSpecificParametersEntity;
import com.rte_france.antares.datamanager_back.service.impl.ThermalFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.impl.ThermalSpecificFileProcessorServiceImpl;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.rte_france.antares.datamanager_back.util.Utils.OTHERS_AREA;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ThermalSpecificFileProcessorServiceImplTest {

    private static final String HORIZON = "2025";
    private static final String TRAJECTORY_NAME = "thermal_specific_test";

    @TempDir
    Path tempDir;

    @Mock
    private ThermalFileProcessorServiceImpl thermalFileProcessorService;

    @Mock
    private AreaRepository areaRepository;

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
        when(thermalFileProcessorService.clusterExistsByName(anyString())).thenReturn(true);
        when(thermalFileProcessorService.findOrCreateThermalClusterRef(any(), anyString(), anyString()))
                .thenReturn(ThermalClusterRef.builder().id(1).name("Cluster1").namePemmdb("PEM1").build());

        Path file = writeWorkbookToTemp(createValidWorkbook(2));

        List<ThermalSpecificParametersEntity> result = service.buildThermalSpecificParameterValueList(TRAJECTORY_NAME, file, HORIZON, "FR", 1);

        assertEquals(2, result.size());
        // Basic field checks
        assertEquals("FR", result.get(0).getNode());
        assertEquals("Cluster1", result.get(0).getThermalClusterRef().getName());
        assertEquals(1.0, result.get(0).getMinStableGeneration());
        assertEquals(39.0, result.get(0).getP12());
    }

    @Test
    void shouldThrowWhenClusterCellEmpty() throws IOException {
        when(thermalFileProcessorService.clusterExistsByName(anyString())).thenReturn(true);
        Path file = writeWorkbookToTemp(createValidWorkbook(1, true, false));
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.buildThermalSpecificParameterValueList(TRAJECTORY_NAME, file, HORIZON, "FR", 1)
        );
        assertTrue(ex.getMessage().startsWith("Cluster  does not exist"));
    }

    @Test
    void shouldThrowWhenClusterDoesNotExist() throws IOException {
        when(thermalFileProcessorService.clusterExistsByName(anyString())).thenReturn(false);
        Path file = writeWorkbookToTemp(createValidWorkbook(1));
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.buildThermalSpecificParameterValueList(TRAJECTORY_NAME, file, HORIZON, "FR", 1)
        );
        assertTrue(ex.getMessage().contains("Cluster Cluster1 does not exist"));
    }

    @Test
    void shouldThrowForOthersAreaIfNoStudyAreaPresent() throws IOException {
        when(thermalFileProcessorService.clusterExistsByName(anyString())).thenReturn(true);
        when(thermalFileProcessorService.findOrCreateThermalClusterRef(any(), anyString(), anyString()))
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
        when(thermalFileProcessorService.clusterExistsByName(anyString())).thenReturn(true);
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
    void shouldThrowRegardlessOfSelectedAreaIfNoStudyAreaPresent() throws IOException {
        when(thermalFileProcessorService.clusterExistsByName(anyString())).thenReturn(true);
        when(thermalFileProcessorService.findOrCreateThermalClusterRef(any(), anyString(), anyString()))
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
            // 0..4 textual
            row.createCell(0).setCellValue(r == 0 ? "FR" : "DE");
            row.createCell(1).setCellValue("ENTSOE");
            row.createCell(2).setCellValue("comment");
            row.createCell(3).setCellValue("PEM1");
            if (makeClusterEmpty) {
                row.createCell(4).setCellValue("");
            } else {
                row.createCell(4).setCellValue("Cluster1");
            }

            // numeric columns 5..43
            int v = 1;
            for (int c = 5; c <= 43; c++) {
                row.createCell(c).setCellValue(v++);
            }
        }
        return wb;
    }

    private static String[] buildHeaders() {
        String[] base = new String[]{
                "node",
                "node_ENTSOE",
                "comments",
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
                "nb_unit",
                "PO_winter_rate"
        };
        String[] headers = new String[44];
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
}
