package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.p2g.impl.P2gFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.util.Utils.FormulaAndValue;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class P2gFileProcessorServiceImplTest {

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private AreaRepository areaRepository;

    @Mock
    private TrajectoryServiceImpl trajectoryService;

    private P2gFileProcessorServiceImpl service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new P2gFileProcessorServiceImpl(
                trajectoryRepository,
                areaRepository,
                trajectoryService
        );
        lenient().when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // -------------------------------------------------------------------------
    // Tests pour getFormulaAndValue
    // -------------------------------------------------------------------------

    @Test
    void testGetFormulaAndValueWithNullCell() {
        FormulaAndValue result = service.getFormulaAndValue(null);
        assertThat(result).isNotNull();
        assertThat(result.formula()).isNull();
        assertThat(result.value()).isNull();
        assertThat(result.hasFormula()).isFalse();
        assertThat(result.hasValue()).isFalse();
        assertThat(result.getNumericValue()).isNull();
        assertThat(result.getStringValue()).isNull();
        assertThat(result.getBooleanValue()).isNull();
    }

    @Test
    void testGetFormulaAndValueWithNumericCell() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Test");
            Row row = sheet.createRow(0);
            Cell cell = row.createCell(0);
            cell.setCellValue(123.45);

            FormulaAndValue result = service.getFormulaAndValue(cell);
            assertThat(result).isNotNull();
            assertThat(result.hasFormula()).isFalse();
            assertThat(result.hasValue()).isTrue();
            assertThat(result.formula()).isNull();
            assertThat(result.value()).isEqualTo(123.45);
            assertThat(result.getNumericValue()).isEqualTo(123.45);
            assertThat(result.getStringValue()).isEqualTo("123.45");
        }
    }

    @Test
    void testGetFormulaAndValueWithStringCell() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Test");
            Row row = sheet.createRow(0);
            Cell cell = row.createCell(0);
            cell.setCellValue("hello");

            FormulaAndValue result = service.getFormulaAndValue(cell);
            assertThat(result).isNotNull();
            assertThat(result.hasFormula()).isFalse();
            assertThat(result.hasValue()).isTrue();
            assertThat(result.value()).isEqualTo("hello");
            assertThat(result.getStringValue()).isEqualTo("hello");
            assertThat(result.getNumericValue()).isNull();
        }
    }

    @Test
    void testGetFormulaAndValueWithBooleanCell() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Test");
            Row row = sheet.createRow(0);
            Cell cell = row.createCell(0);
            cell.setCellValue(true);

            FormulaAndValue result = service.getFormulaAndValue(cell);
            assertThat(result).isNotNull();
            assertThat(result.hasFormula()).isFalse();
            assertThat(result.hasValue()).isTrue();
            assertThat(result.value()).isEqualTo(true);
            assertThat(result.getBooleanValue()).isTrue();
        }
    }

    @Test
    void testGetFormulaAndValueWithCrossSheetFormula() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheetParams = wb.createSheet("parameters");
            Row rowParams = sheetParams.createRow(0);
            rowParams.createCell(1).setCellValue(50.0);

            Sheet sheetHorizon = wb.createSheet("2025-2026");
            Row rowHorizon = sheetHorizon.createRow(0);
            rowHorizon.createCell(0).setCellValue(2.0);
            Cell formulaCell = rowHorizon.createCell(3);
            formulaCell.setCellFormula("parameters!B1*A1");

            FormulaAndValue result = service.getFormulaAndValue(formulaCell);
            assertThat(result).isNotNull();
            assertThat(result.hasFormula()).isTrue();
            assertThat(result.hasValue()).isTrue();
            assertThat(result.formula()).isEqualTo("parameters!B1*A1");
            assertThat(result.getNumericValue()).isEqualTo(100.0);
            assertThat(result.value()).isEqualTo(100.0);
        }
    }

    // -------------------------------------------------------------------------
    // Tests pour loadStudyAreas
    // -------------------------------------------------------------------------

    @Test
    void testLoadStudyAreas() {
        AreaEntity area1 = new AreaEntity();
        area1.setName("area_fr");
        AreaEntity area2 = new AreaEntity();
        area2.setName("AREA_DE");

        when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(area1, area2));

        List<String> areas = service.loadStudyAreas(1);
        assertThat(areas).containsExactly("AREA_FR", "AREA_DE");
    }

    // -------------------------------------------------------------------------
    // Tests pour processModulationP2gFile
    // -------------------------------------------------------------------------

    @Test
    void testProcessModulationP2gFile_Success() throws IOException {
        Path trajectoryDir = tempDir.resolve("trajMod");
        Files.createDirectories(trajectoryDir);

        String csvFileName = "MB_MC_modulation_trajMod_2026.csv";
        Files.createFile(trajectoryDir.resolve(csvFileName));

        when(trajectoryService.normalizeAndValidateDirectory(eq(TrajectoryType.P2G_MARKET_MODULATION), any(), any()))
                .thenReturn(tempDir);

        TrajectoryEntity mockTrajectory = TrajectoryEntity.builder()
                .fileName("trajMod")
                .type(TrajectoryType.P2G_MARKET_MODULATION.name())
                .build();

        when(trajectoryService.buildDirectoryTrajectory(
                eq(TrajectoryType.P2G_MARKET_MODULATION.name()),
                eq("trajMod"),
                eq(trajectoryDir),
                eq("2025-2026"),
                isNull(),
                isNull()
        )).thenReturn(mockTrajectory);

        TrajectoryEntity result = service.processModulationP2gFile("trajMod", "2025-2026", 1, false);

        assertThat(result).isNotNull();
        assertThat(result.getFileName()).isEqualTo("trajMod");
        verify(trajectoryRepository).save(mockTrajectory);
    }

    @Test
    void testProcessModulationP2gFile_MissingFile() throws IOException {
        Path trajectoryDir = tempDir.resolve("trajModMissing");
        Files.createDirectories(trajectoryDir);

        when(trajectoryService.normalizeAndValidateDirectory(eq(TrajectoryType.P2G_MARKET_MODULATION), any(), any()))
                .thenReturn(tempDir);

        assertThatThrownBy(() -> service.processModulationP2gFile("trajModMissing", "2025-2026", 1, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Missing")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.BAD_REQUEST);
    }

    // -------------------------------------------------------------------------
    // Tests pour processCapacityP2gFile
    // -------------------------------------------------------------------------

    @Test
    void testProcessCapacityP2gFile_MissingFiles() throws IOException {
        Path trajectoryDir = tempDir.resolve("trajCapMissing");
        Files.createDirectories(trajectoryDir);

        when(trajectoryService.normalizeAndValidateDirectory(eq(TrajectoryType.P2G_CAPACITY_COST), any(), any()))
                .thenReturn(tempDir);

        assertThatThrownBy(() -> service.processCapacityP2gFile("trajCapMissing", "2025-2026", 1, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Required files are missing")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.BAD_REQUEST);
    }

    @Test
    void testProcessCapacityP2gFile_Success() throws IOException {
        Path trajectoryDir = tempDir.resolve("trajCapSuccess");
        Files.createDirectories(trajectoryDir);

        when(trajectoryService.normalizeAndValidateDirectory(eq(TrajectoryType.P2G_CAPACITY_COST), any(), any()))
                .thenReturn(tempDir);

        TrajectoryEntity mockTrajectory = TrajectoryEntity.builder()
                .fileName("trajCapSuccess")
                .type(TrajectoryType.P2G_CAPACITY_COST.name())
                .build();

        when(trajectoryService.buildDirectoryTrajectory(
                eq(TrajectoryType.P2G_CAPACITY_COST.name()),
                eq("trajCapSuccess"),
                eq(trajectoryDir),
                eq("2025-2026"),
                isNull(),
                isNull()
        )).thenReturn(mockTrajectory);

        AreaEntity areaEntity = new AreaEntity();
        areaEntity.setName("FR");
        when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(areaEntity));

        // 1. Créer P2G_capacity.xlsx
        Path capacityFile = trajectoryDir.resolve("P2G_capacity.xlsx");
        try (Workbook wbCap = new XSSFWorkbook()) {
            // Onglet parameters
            Sheet paramsSheet = wbCap.createSheet("parameters");
            Row headerParams = paramsSheet.createRow(0);
            headerParams.createCell(0).setCellValue("parameter");
            headerParams.createCell(1).setCellValue("2025-2026");

            String[] params = {"FC_electrolyseur", "Facteur_surdimension_ENR", "Part_PV_mix"};
            for (int i = 0; i < params.length; i++) {
                Row r = paramsSheet.createRow(i + 1);
                r.createCell(0).setCellValue(params[i]);
                r.createCell(1).setCellValue(0.8 + i);
            }

            // Onglet horizon "2025-2026" avec une formule cross-sheet vers "parameters"
            Sheet horizonSheet = wbCap.createSheet("2025-2026");
            Row headerHorizon = horizonSheet.createRow(0);
            headerHorizon.createCell(2).setCellValue("area");
            headerHorizon.createCell(3).setCellValue("P2G_fatalband");
            headerHorizon.createCell(4).setCellValue("P2G_asservi");
            headerHorizon.createCell(6).setCellValue("P2G_methanation");
            headerHorizon.createCell(7).setCellValue("P2G_base_eff");
            headerHorizon.createCell(9).setCellValue("To_Links_p2G_marg");
            headerHorizon.createCell(10).setCellValue("To_Links_p2G_base (P2G base + fatal)");

            Row dataRow = horizonSheet.createRow(1);
            dataRow.createCell(2).setCellValue("FR");
            // Formule cross-sheet : lit parameters!B2
            Cell fatalCell = dataRow.createCell(3);
            fatalCell.setCellFormula("parameters!B2");
            dataRow.createCell(4).setCellValue(20.0);
            dataRow.createCell(6).setCellValue(30.0);
            dataRow.createCell(7).setCellValue(40.0);
            dataRow.createCell(9).setCellValue(50.0);
            dataRow.createCell(10).setCellValue(60.0);

            try (FileOutputStream fos = new FileOutputStream(capacityFile.toFile())) {
                wbCap.write(fos);
            }
        }

        // 2. Créer P2G_costs.xlsx
        Path costsFile = trajectoryDir.resolve("P2G_costs.xlsx");
        try (Workbook wbCost = new XSSFWorkbook()) {
            Sheet costsSheet = wbCost.createSheet("costs");
            Row headerCosts = costsSheet.createRow(0);
            headerCosts.createCell(0).setCellValue("type P2G");
            headerCosts.createCell(1).setCellValue("other");
            headerCosts.createCell(2).setCellValue("modulation");
            headerCosts.createCell(3).setCellValue("2026");

            String[] types = {"Marginal", "Base", "Asservi", "Methanation"};
            for (int i = 0; i < types.length; i++) {
                Row r = costsSheet.createRow(i + 1);
                r.createCell(0).setCellValue(types[i]);
                r.createCell(2).setCellValue("mod_" + types[i]);
                r.createCell(3).setCellValue(15.5 * (i + 1));
            }

            try (FileOutputStream fos = new FileOutputStream(costsFile.toFile())) {
                wbCost.write(fos);
            }
        }

        TrajectoryEntity result = service.processCapacityP2gFile("trajCapSuccess", "2025-2026", 1, false);

        assertThat(result).isNotNull();
        assertThat(result.getP2gParametersEntities()).hasSize(1);
        assertThat(result.getP2gCapacityEntities()).hasSize(1);
        assertThat(result.getP2gCapacityEntities().get(0).getBaseFatalBand()).isEqualTo(0.8);
        assertThat(result.getP2gCostEntities()).hasSize(4);
        verify(trajectoryRepository).save(mockTrajectory);
    }

    @Test
    void testProcessCapacityP2gFile_MissingHorizonTab() throws IOException {
        Path trajectoryDir = tempDir.resolve("trajCapMissingHorizon");
        Files.createDirectories(trajectoryDir);

        when(trajectoryService.normalizeAndValidateDirectory(eq(TrajectoryType.P2G_CAPACITY_COST), any(), any()))
                .thenReturn(tempDir);

        TrajectoryEntity mockTrajectory = TrajectoryEntity.builder()
                .fileName("trajCapMissingHorizon")
                .type(TrajectoryType.P2G_CAPACITY_COST.name())
                .build();

        when(trajectoryService.buildDirectoryTrajectory(any(), any(), any(), any(), any(), any()))
                .thenReturn(mockTrajectory);

        Path capacityFile = trajectoryDir.resolve("P2G_capacity.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet paramsSheet = wb.createSheet("parameters");
            Row headerParams = paramsSheet.createRow(0);
            headerParams.createCell(0).setCellValue("parameter");
            headerParams.createCell(1).setCellValue("2025-2026");
            String[] params = {"FC_electrolyseur", "Facteur_surdimension_ENR", "Part_PV_mix"};
            for (int i = 0; i < params.length; i++) {
                Row r = paramsSheet.createRow(i + 1);
                r.createCell(0).setCellValue(params[i]);
                r.createCell(1).setCellValue(0.8 + i);
            }
            try (FileOutputStream fos = new FileOutputStream(capacityFile.toFile())) {
                wb.write(fos);
            }
        }

        Path costsFile = trajectoryDir.resolve("P2G_costs.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet costsSheet = wb.createSheet("costs");
            Row headerCosts = costsSheet.createRow(0);
            headerCosts.createCell(0).setCellValue("type P2G");
            headerCosts.createCell(1).setCellValue("other");
            headerCosts.createCell(2).setCellValue("modulation");
            headerCosts.createCell(3).setCellValue("2026");
            String[] types = {"Marginal", "Base", "Asservi", "Methanation"};
            for (int i = 0; i < types.length; i++) {
                Row r = costsSheet.createRow(i + 1);
                r.createCell(0).setCellValue(types[i]);
                r.createCell(2).setCellValue("mod_" + types[i]);
                r.createCell(3).setCellValue(15.5 * (i + 1));
            }
            try (FileOutputStream fos = new FileOutputStream(costsFile.toFile())) {
                wb.write(fos);
            }
        }

        assertThatThrownBy(() -> service.processCapacityP2gFile("trajCapMissingHorizon", "2025-2026", 1, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not exist in the P2G Capacity trajectory")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.BAD_REQUEST);
    }

    @Test
    void testProcessCapacityP2gFile_NoAreaPresent() throws IOException {
        Path trajectoryDir = tempDir.resolve("trajCapNoArea");
        Files.createDirectories(trajectoryDir);

        when(trajectoryService.normalizeAndValidateDirectory(eq(TrajectoryType.P2G_CAPACITY_COST), any(), any()))
                .thenReturn(tempDir);

        TrajectoryEntity mockTrajectory = TrajectoryEntity.builder()
                .fileName("trajCapNoArea")
                .type(TrajectoryType.P2G_CAPACITY_COST.name())
                .build();

        when(trajectoryService.buildDirectoryTrajectory(any(), any(), any(), any(), any(), any()))
                .thenReturn(mockTrajectory);

        when(areaRepository.findAllByStudyId(1)).thenReturn(List.of());

        Path capacityFile = trajectoryDir.resolve("P2G_capacity.xlsx");
        try (Workbook wbCap = new XSSFWorkbook()) {
            Sheet paramsSheet = wbCap.createSheet("parameters");
            Row headerParams = paramsSheet.createRow(0);
            headerParams.createCell(0).setCellValue("parameter");
            headerParams.createCell(1).setCellValue("2025-2026");
            String[] params = {"FC_electrolyseur", "Facteur_surdimension_ENR", "Part_PV_mix"};
            for (int i = 0; i < params.length; i++) {
                Row r = paramsSheet.createRow(i + 1);
                r.createCell(0).setCellValue(params[i]);
                r.createCell(1).setCellValue(0.8 + i);
            }

            Sheet horizonSheet = wbCap.createSheet("2025-2026");
            Row headerHorizon = horizonSheet.createRow(0);
            headerHorizon.createCell(2).setCellValue("area");
            headerHorizon.createCell(3).setCellValue("P2G_fatalband");
            headerHorizon.createCell(4).setCellValue("P2G_asservi");
            headerHorizon.createCell(6).setCellValue("P2G_methanation");
            headerHorizon.createCell(7).setCellValue("P2G_base_eff");
            headerHorizon.createCell(9).setCellValue("To_Links_p2G_marg");
            headerHorizon.createCell(10).setCellValue("To_Links_p2G_base (P2G base + fatal)");

            Row dataRow = horizonSheet.createRow(1);
            dataRow.createCell(2).setCellValue("UNKNOWN_AREA");
            dataRow.createCell(3).setCellValue(10.0);
            dataRow.createCell(4).setCellValue(20.0);
            dataRow.createCell(6).setCellValue(30.0);
            dataRow.createCell(7).setCellValue(40.0);
            dataRow.createCell(9).setCellValue(50.0);
            dataRow.createCell(10).setCellValue(60.0);

            try (FileOutputStream fos = new FileOutputStream(capacityFile.toFile())) {
                wbCap.write(fos);
            }
        }

        Path costsFile = trajectoryDir.resolve("P2G_costs.xlsx");
        try (Workbook wbCost = new XSSFWorkbook()) {
            Sheet costsSheet = wbCost.createSheet("costs");
            Row headerCosts = costsSheet.createRow(0);
            headerCosts.createCell(0).setCellValue("type P2G");
            headerCosts.createCell(1).setCellValue("other");
            headerCosts.createCell(2).setCellValue("modulation");
            headerCosts.createCell(3).setCellValue("2026");
            String[] types = {"Marginal", "Base", "Asservi", "Methanation"};
            for (int i = 0; i < types.length; i++) {
                Row r = costsSheet.createRow(i + 1);
                r.createCell(0).setCellValue(types[i]);
                r.createCell(2).setCellValue("mod_" + types[i]);
                r.createCell(3).setCellValue(15.5 * (i + 1));
            }
            try (FileOutputStream fos = new FileOutputStream(costsFile.toFile())) {
                wbCost.write(fos);
            }
        }

        assertThatThrownBy(() -> service.processCapacityP2gFile("trajCapNoArea", "2025-2026", 1, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No area of the study is present")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.BAD_REQUEST);
    }

    @Test
    void testProcessCapacityP2gFile_NonNumericColumnInHorizon() throws IOException {
        Path trajectoryDir = tempDir.resolve("trajCapNonNumericHorizon");
        Files.createDirectories(trajectoryDir);

        when(trajectoryService.normalizeAndValidateDirectory(eq(TrajectoryType.P2G_CAPACITY_COST), any(), any()))
                .thenReturn(tempDir);

        TrajectoryEntity mockTrajectory = TrajectoryEntity.builder()
                .fileName("trajCapNonNumericHorizon")
                .type(TrajectoryType.P2G_CAPACITY_COST.name())
                .build();

        when(trajectoryService.buildDirectoryTrajectory(any(), any(), any(), any(), any(), any()))
                .thenReturn(mockTrajectory);

        AreaEntity areaEntity = new AreaEntity();
        areaEntity.setName("FR");
        when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(areaEntity));

        Path capacityFile = trajectoryDir.resolve("P2G_capacity.xlsx");
        try (Workbook wbCap = new XSSFWorkbook()) {
            Sheet paramsSheet = wbCap.createSheet("parameters");
            Row headerParams = paramsSheet.createRow(0);
            headerParams.createCell(0).setCellValue("parameter");
            headerParams.createCell(1).setCellValue("2025-2026");
            String[] params = {"FC_electrolyseur", "Facteur_surdimension_ENR", "Part_PV_mix"};
            for (int i = 0; i < params.length; i++) {
                Row r = paramsSheet.createRow(i + 1);
                r.createCell(0).setCellValue(params[i]);
                r.createCell(1).setCellValue(0.8 + i);
            }

            Sheet horizonSheet = wbCap.createSheet("2025-2026");
            Row headerHorizon = horizonSheet.createRow(0);
            headerHorizon.createCell(2).setCellValue("area");
            headerHorizon.createCell(3).setCellValue("P2G_fatalband");
            headerHorizon.createCell(4).setCellValue("P2G_asservi");
            headerHorizon.createCell(6).setCellValue("P2G_methanation");
            headerHorizon.createCell(7).setCellValue("P2G_base_eff");
            headerHorizon.createCell(9).setCellValue("To_Links_p2G_marg");
            headerHorizon.createCell(10).setCellValue("To_Links_p2G_base (P2G base + fatal)");

            Row dataRow = horizonSheet.createRow(1);
            dataRow.createCell(2).setCellValue("FR");
            dataRow.createCell(3).setCellValue("NOT_A_NUMBER");
            dataRow.createCell(4).setCellValue(20.0);
            dataRow.createCell(6).setCellValue(30.0);
            dataRow.createCell(7).setCellValue(40.0);
            dataRow.createCell(9).setCellValue(50.0);
            dataRow.createCell(10).setCellValue(60.0);

            try (FileOutputStream fos = new FileOutputStream(capacityFile.toFile())) {
                wbCap.write(fos);
            }
        }

        Path costsFile = trajectoryDir.resolve("P2G_costs.xlsx");
        try (Workbook wbCost = new XSSFWorkbook()) {
            Sheet costsSheet = wbCost.createSheet("costs");
            Row headerCosts = costsSheet.createRow(0);
            headerCosts.createCell(0).setCellValue("type P2G");
            headerCosts.createCell(1).setCellValue("other");
            headerCosts.createCell(2).setCellValue("modulation");
            headerCosts.createCell(3).setCellValue("2026");
            String[] types = {"Marginal", "Base", "Asservi", "Methanation"};
            for (int i = 0; i < types.length; i++) {
                Row r = costsSheet.createRow(i + 1);
                r.createCell(0).setCellValue(types[i]);
                r.createCell(2).setCellValue("mod_" + types[i]);
                r.createCell(3).setCellValue(15.5 * (i + 1));
            }
            try (FileOutputStream fos = new FileOutputStream(costsFile.toFile())) {
                wbCost.write(fos);
            }
        }

        assertThatThrownBy(() -> service.processCapacityP2gFile("trajCapNonNumericHorizon", "2025-2026", 1, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must be numeric in horizon tab")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.BAD_REQUEST);
    }
}
