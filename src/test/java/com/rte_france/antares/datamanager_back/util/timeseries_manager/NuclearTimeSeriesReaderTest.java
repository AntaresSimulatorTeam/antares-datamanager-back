package com.rte_france.antares.datamanager_back.util.timeseries_manager;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import org.apache.poi.ss.usermodel.*;
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

class NuclearTimeSeriesReaderTest {

    private NuclearTimeSeriesReader reader;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        reader = new NuclearTimeSeriesReader();
    }

    @Test
    void readFromXlsx_shouldThrowException_whenFileNotFound() {
        Path nonExistent = tempDir.resolve("missing.xlsx");
        assertThatThrownBy(() -> reader.readFromXlsx(nonExistent, "2030", true))
                .isInstanceOf(TechnicalException.class)
                .hasMessageContaining("File not found");
    }

    @Test
    void readFromXlsx_shouldThrowException_whenNoSheets() throws IOException {
        Path emptyFile = tempDir.resolve("no_sheets.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            // Workbook creation without sheets might be tricky with XSSFWorkbook, 
            // but POI usually prevents writing empty workbooks or they are invalid.
            // We'll try to create a workbook and not add any sheet.
            try (FileOutputStream fos = new FileOutputStream(emptyFile.toFile())) {
                wb.write(fos);
            }
        }
        
        // Actually XSSFWorkbook.write() might fail if no sheets. Let's check if we can reproduce it.
        assertThatThrownBy(() -> reader.readFromXlsx(emptyFile, "2030", true))
                .isInstanceOf(TechnicalException.class);
    }

    @Test
    void readFromXlsx_shouldThrowException_whenSheetNotFound() throws IOException {
        Path file = tempDir.resolve("sheet_missing.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet("Existing");
            try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                wb.write(fos);
            }
        }

        assertThatThrownBy(() -> reader.readFromXlsx(file, "Missing", true))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void readFromXlsx_shouldUseFirstSheet_whenHorizonNotProvided() throws IOException {
        Path file = tempDir.resolve("default_sheet.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("First");
            s.createRow(0).createCell(0).setCellValue("Header");
            s.createRow(1).createCell(0).setCellValue(123.456);
            try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                wb.write(fos);
            }
        }

        TimeSeriesMatrix matrix = reader.readFromXlsx(file, null, true);
        assertThat(matrix.columns()).hasSize(1);
        assertThat(matrix.columns().get(0).name()).isEqualTo("Header");
        assertThat(matrix.columns().get(0).values()[0]).isEqualTo(123.46);
    }

    @Test
    void readFromXlsx_shouldHandleDifferentCellTypes() throws IOException {
        Path file = tempDir.resolve("cell_types.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("2030");
            Row header = s.createRow(0);
            header.createCell(0).setCellValue("Numeric");
            header.createCell(1).setCellValue("String");
            header.createCell(2).setCellValue("Formula");
            header.createCell(3).setCellValue("Empty");
            header.createCell(4).setCellValue("FormulaString");

            Row data = s.createRow(1);
            data.createCell(0).setCellValue(1.234);
            data.createCell(1).setCellValue("5.678");
            data.createCell(2).setCellFormula("10+0.555");
            // Cell 3 is empty
            data.createCell(4).setCellFormula("\"100.123\"");

            // Evaluate formulas
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            evaluator.evaluateAll();

            try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                wb.write(fos);
            }
        }

        TimeSeriesMatrix matrix = reader.readFromXlsx(file, "2030", true);
        assertThat(matrix.columns()).hasSize(5);
        assertThat(matrix.columns().get(0).values()[0]).isEqualTo(1.23);
        assertThat(matrix.columns().get(1).values()[0]).isEqualTo(5.68);
        assertThat(matrix.columns().get(2).values()[0]).isEqualTo(10.56);
        assertThat(matrix.columns().get(3).values()[0]).isEqualTo(0.0);
        assertThat(matrix.columns().get(4).values()[0]).isEqualTo(100.12);
    }

    @Test
    void readFromXlsx_shouldHandleNoHeader() throws IOException {
        Path file = tempDir.resolve("no_header.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("2030");
            Row r = s.createRow(0);
            r.createCell(0).setCellValue(10.1);
            r.createCell(1).setCellValue(20.2);
            try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                wb.write(fos);
            }
        }

        TimeSeriesMatrix matrix = reader.readFromXlsx(file, "2030", false);
        assertThat(matrix.columns()).hasSize(2);
        assertThat(matrix.columns().get(0).name()).isEqualTo("Column0");
        assertThat(matrix.columns().get(0).values()[0]).isEqualTo(10.1);
    }

    @Test
    void readFromXlsx_shouldLimitRowsToMaxRowsPerYear() throws IOException {
        Path file = tempDir.resolve("large.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("2030");
            s.createRow(0).createCell(0).setCellValue("Header");
            for (int i = 1; i <= 9000; i++) {
                s.createRow(i).createCell(0).setCellValue(i);
            }
            try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                wb.write(fos);
            }
        }

        TimeSeriesMatrix matrix = reader.readFromXlsx(file, "2030", true);
        assertThat(matrix.getRowCount()).isEqualTo(8784);
    }

    @Test
    void readFromXlsx_shouldHandleInvalidStringNumbers() throws IOException {
        Path file = tempDir.resolve("invalid_string.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("2030");
            s.createRow(0).createCell(0).setCellValue("Invalid");
            s.createRow(1).createCell(0).setCellValue("not a number");
            try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                wb.write(fos);
            }
        }

        TimeSeriesMatrix matrix = reader.readFromXlsx(file, "2030", true);
        assertThat(matrix.columns().get(0).values()[0]).isEqualTo(0.0);
    }
    @Test
    void readFromXlsx_shouldThrowException_whenSheetIsEmpty() throws IOException {
        Path file = tempDir.resolve("empty_sheet.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet("EmptySheet");
            try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                wb.write(fos);
            }
        }

        assertThatThrownBy(() -> reader.readFromXlsx(file, "EmptySheet", true))
                .isInstanceOf(TechnicalException.class)
                .hasMessageContaining("Excel sheet is empty");
    }

    @Test
    void readFromXlsx_shouldHandleMissingCellsInRow() throws IOException {
        Path file = tempDir.resolve("missing_cells.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("2030");
            Row r0 = s.createRow(0);
            r0.createCell(0).setCellValue("H1");
            r0.createCell(1).setCellValue("H2");

            Row r1 = s.createRow(1);
            r1.createCell(0).setCellValue(10.5);
            // Cell 1 is null

            try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                wb.write(fos);
            }
        }

        TimeSeriesMatrix matrix = reader.readFromXlsx(file, "2030", true);
        assertThat(matrix.columns()).hasSize(2);
        assertThat(matrix.columns().get(0).values()[0]).isEqualTo(10.5);
        assertThat(matrix.columns().get(1).values()[0]).isEqualTo(0.0);
    }
    @Test
    void readFromXlsx_shouldRoundToTwoDecimals() throws Exception {
        Path file = tempDir.resolve("rounding_test_nuclear.xlsx");

        double valueManyDecimals = 1234.5678;
        double expectedRoundedValue = 1234.57;

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("2030");
            Row header = s.createRow(0);
            header.createCell(0).setCellValue("Value");

            Row r1 = s.createRow(1);
            r1.createCell(0).setCellValue(valueManyDecimals);

            try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                wb.write(fos);
            }
        }

        var matrix = reader.readFromXlsx(file, "2030", true);

        assertThat(matrix.getRowCount()).isEqualTo(1);
        assertThat(matrix.columns().get(0).values()[0]).isEqualTo(expectedRoundedValue);
    }

    @Test
    void readFromXlsx_shouldKeepTwoDecimals() throws Exception {
        Path file = tempDir.resolve("precision_test_nuclear.xlsx");

        double valueWithTwoDecimals = 1545.48;

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("2030");
            Row header = s.createRow(0);
            header.createCell(0).setCellValue("Value");

            Row r1 = s.createRow(1);
            Cell cell = r1.createCell(0);
            cell.setCellValue(valueWithTwoDecimals);

            CellStyle style = wb.createCellStyle();
            DataFormat format = wb.createDataFormat();
            style.setDataFormat(format.getFormat("0"));
            cell.setCellStyle(style);

            try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                wb.write(fos);
            }
        }

        var matrix = reader.readFromXlsx(file, "2030", true);

        assertThat(matrix.getRowCount()).isEqualTo(1);
        assertThat(matrix.columns().get(0).values()[0]).isEqualTo(valueWithTwoDecimals);
    }
}
