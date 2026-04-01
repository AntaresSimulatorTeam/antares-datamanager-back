package com.rte_france.antares.datamanager_back.service.sts.impl;

import com.rte_france.antares.datamanager_back.repository.model.StConstraintsHoursEntity;
import com.rte_france.antares.datamanager_back.repository.model.StConstraintsParameterEntity;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StStorageConstraintsFileProcessorServiceImplTest {

    private StStorageConstraintsFileProcessorServiceImpl service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new StStorageConstraintsFileProcessorServiceImpl();
    }

    @Test
    void shouldProcessFullFileSuccessfully() throws IOException {
        Path filePath = tempDir.resolve("constraints.xlsx");
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet paramSheet = workbook.createSheet("parameters");
            Row headerP = paramSheet.createRow(0);
            headerP.createCell(0).setCellValue("name");
            headerP.createCell(1).setCellValue("zone");
            headerP.createCell(2).setCellValue("cluster");
            headerP.createCell(3).setCellValue("variable");
            headerP.createCell(4).setCellValue("operator");
            headerP.createCell(5).setCellValue("enabled");

            Row rowP = paramSheet.createRow(1);
            rowP.createCell(0).setCellValue("param1");
            rowP.createCell(1).setCellValue("zone1");
            rowP.createCell(2).setCellValue("cluster1");
            rowP.createCell(3).setCellValue("var1");
            rowP.createCell(4).setCellValue("=");
            rowP.createCell(5).setCellValue(true);

            Sheet hourSheet = workbook.createSheet("hours");
            Row headerH = hourSheet.createRow(0);
            headerH.createCell(0).setCellValue("name");
            headerH.createCell(1).setCellValue("zone");
            headerH.createCell(2).setCellValue("cluster");
            headerH.createCell(3).setCellValue("occurrence");
            headerH.createCell(4).setCellValue("start_hour");
            headerH.createCell(5).setCellValue("end_hour");

            Row rowH = hourSheet.createRow(1);
            rowH.createCell(0).setCellValue("param1");
            rowH.createCell(1).setCellValue("zone1");
            rowH.createCell(2).setCellValue("cluster1");
            rowH.createCell(3).setCellValue(1);
            rowH.createCell(4).setCellValue(10);
            rowH.createCell(5).setCellValue(20);

            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                workbook.write(fos);
            }
        }

        List<StConstraintsParameterEntity> result = service.processConstraintsParametersAnHoursFile(filePath);

        assertThat(result).hasSize(1);
        StConstraintsParameterEntity p = result.get(0);
        assertThat(p.getName()).isEqualTo("param1");
        assertThat(p.getZone()).isEqualTo("zone1");
        assertThat(p.getCluster()).isEqualTo("cluster1");
        assertThat(p.getVariable()).isEqualTo("var1");
        assertThat(p.getOperator()).isEqualTo("=");
        assertThat(p.getEnabled()).isTrue();
        assertThat(p.getHours()).hasSize(1);
        
        StConstraintsHoursEntity h = p.getHours().get(0);
        assertThat(h.getOccurrence()).isEqualTo(1);
        assertThat(h.getStartHour()).isEqualTo(10);
        assertThat(h.getEndHour()).isEqualTo(20);
        assertThat(h.getParameter()).isEqualTo(p);
    }

    @Test
    void shouldHandleMissingSheetsGracefully() throws IOException {
        Path filePath = tempDir.resolve("empty.xlsx");
        try (Workbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("other");
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                workbook.write(fos);
            }
        }

        List<StConstraintsParameterEntity> result = service.processConstraintsParametersAnHoursFile(filePath);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldThrowWhenHoursSheetHasMissingParameterInfo() throws IOException {
        Path filePath = tempDir.resolve("missing_info.xlsx");
        try (Workbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("parameters");
            Sheet hourSheet = workbook.createSheet("hours");
            hourSheet.createRow(0); // header
            Row row = hourSheet.createRow(1);
            row.createCell(0).setCellValue("param1");
            // zone and cluster missing

            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                workbook.write(fos);
            }
        }

        assertThatThrownBy(() -> service.processConstraintsParametersAnHoursFile(filePath))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Parameter zone missing");
    }

    @Test
    void shouldThrowWhenHoursReferToNonExistentParameter() throws IOException {
        Path filePath = tempDir.resolve("orphaned_hour.xlsx");
        try (Workbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("parameters");
            Sheet hourSheet = workbook.createSheet("hours");
            hourSheet.createRow(0);
            Row row = hourSheet.createRow(1);
            row.createCell(0).setCellValue("ghost");
            row.createCell(1).setCellValue("zone");
            row.createCell(2).setCellValue("cluster");

            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                workbook.write(fos);
            }
        }

        assertThatThrownBy(() -> service.processConstraintsParametersAnHoursFile(filePath))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Parameter not found");
    }

    @Test
    void shouldCorrectlyParseDifferentCellTypesForEnabled() throws IOException {
        Path filePath = tempDir.resolve("types.xlsx");
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet paramSheet = workbook.createSheet("parameters");
            paramSheet.createRow(0); // header
            
            // Boolean
            Row r1 = paramSheet.createRow(1);
            r1.createCell(0).setCellValue("p1");
            r1.createCell(1).setCellValue("z1");
            r1.createCell(2).setCellValue("c1");
            r1.createCell(5).setCellValue(true);

            // String
            Row r2 = paramSheet.createRow(2);
            r2.createCell(0).setCellValue("p2");
            r2.createCell(1).setCellValue("z1");
            r2.createCell(2).setCellValue("c1");
            r2.createCell(5).setCellValue("true");

            // Double
            Row r3 = paramSheet.createRow(3);
            r3.createCell(0).setCellValue("p3");
            r3.createCell(1).setCellValue("z1");
            r3.createCell(2).setCellValue("c1");
            r3.createCell(5).setCellValue(1.0);

            // Default
            Row r4 = paramSheet.createRow(4);
            r4.createCell(0).setCellValue("p4");
            r4.createCell(1).setCellValue("z1");
            r4.createCell(2).setCellValue("c1");
            // empty cell 5

            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                workbook.write(fos);
            }
        }

        List<StConstraintsParameterEntity> result = service.processConstraintsParametersAnHoursFile(filePath);
        assertThat(result).hasSize(4);
        assertThat(result.get(0).getEnabled()).isTrue();
        assertThat(result.get(1).getEnabled()).isTrue();
        assertThat(result.get(2).getEnabled()).isTrue();
        assertThat(result.get(3).getEnabled()).isFalse();
    }

    @Test
    void shouldCorrectlyParseDifferentCellTypesForNumeric() throws IOException {
        Path filePath = tempDir.resolve("numeric_types.xlsx");
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet paramSheet = workbook.createSheet("parameters");
            paramSheet.createRow(0);
            Row pr = paramSheet.createRow(1);
            pr.createCell(0).setCellValue("p1");
            pr.createCell(1).setCellValue("z1");
            pr.createCell(2).setCellValue("c1");

            Sheet hourSheet = workbook.createSheet("hours");
            hourSheet.createRow(0);
            
            // Double cell
            Row hr1 = hourSheet.createRow(1);
            hr1.createCell(0).setCellValue("p1");
            hr1.createCell(1).setCellValue("z1");
            hr1.createCell(2).setCellValue("c1");
            hr1.createCell(3).setCellValue(5.0);
            hr1.createCell(4).setCellValue(10.0);
            hr1.createCell(5).setCellValue(20.0);

            // String cell
            Row hr2 = hourSheet.createRow(2);
            hr2.createCell(0).setCellValue("p1");
            hr2.createCell(1).setCellValue("z1");
            hr2.createCell(2).setCellValue("c1");
            hr2.createCell(3).setCellValue("6");
            hr2.createCell(4).setCellValue("11");
            hr2.createCell(5).setCellValue("21");

            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                workbook.write(fos);
            }
        }

        List<StConstraintsParameterEntity> result = service.processConstraintsParametersAnHoursFile(filePath);
        List<StConstraintsHoursEntity> hours = result.get(0).getHours();
        assertThat(hours).hasSize(2);
        
        assertThat(hours.get(0).getOccurrence()).isEqualTo(5);
        assertThat(hours.get(0).getStartHour()).isEqualTo(10);
        assertThat(hours.get(0).getEndHour()).isEqualTo(20);

        assertThat(hours.get(1).getOccurrence()).isEqualTo(6);
        assertThat(hours.get(1).getStartHour()).isEqualTo(11);
        assertThat(hours.get(1).getEndHour()).isEqualTo(21);
    }
}
