package com.rte_france.antares.datamanager_back.util.timeseries_manager;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class TimeSeriesReaderTest {

    private TimeSeriesReader timeSeriesReader;

    @BeforeEach
    void setUp() {
        timeSeriesReader = new TimeSeriesReader();
    }

    @Test
    void readFromXlsx_shouldSkipFirstRow(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("ts_skip.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("2030");
            Row header = s.createRow(0);
            header.createCell(0).setCellValue("Header1");
            header.createCell(1).setCellValue("Header2");

            Row r1 = s.createRow(1);
            r1.createCell(0).setCellValue(10.0);
            r1.createCell(1).setCellValue(20.0);

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }

        var matrix = timeSeriesReader.readFromXlsx(file, "2030");
        assertEquals(8760, matrix.getRowCount());
        assertEquals(2, matrix.columns().size());
        // If first row is ignored, the first data row should be "10.0 20.0"
        assertEquals(10.0, matrix.columns().get(0).values()[0]);
        assertEquals(20.0, matrix.columns().get(1).values()[0]);
    }

    @Test
    void readFromTxt_shouldReadCorrectly(@TempDir Path tempDir) throws IOException {
        var filePath = tempDir.resolve("timeseries.txt");
        Files.createFile(filePath);
        Files.writeString(filePath, "1.0 2.0 3.0\n4.0 5.0 6.0\n7.0 8.0 9.0");
        var matrix = timeSeriesReader.readFromTxt(filePath);

        assertEquals(8760, matrix.getRowCount());
        assertEquals(3, matrix.columns().size());
        assertArrayEquals(new double[]{1.0, 4.0, 7.0}, Arrays.copyOf(matrix.columns().get(0).values(), 3));
        assertArrayEquals(new double[]{3.0, 6.0, 9.0}, Arrays.copyOf(matrix.columns().get(2).values(), 3));
    }

    @Test
    void readFromTxt_shouldThrowExceptionForEmptyFile(@TempDir Path tempDir) throws IOException {
        var filePath = tempDir.resolve("empty.txt");
        Files.createFile(filePath);

        var exception = assertThrows(TechnicalException.class,
                () -> timeSeriesReader.readFromTxt(filePath));
        assertEquals("File is empty", exception.getMessage());
    }

    @Test
    void readFromXlsx_shouldReadSpecificHorizonSheet(@TempDir Path tempDir) throws Exception {
        // create workbook with horizon sheet and mixed cell types
        Path file = tempDir.resolve("ts.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("2030");
            // Header row to be skipped. It MUST have cells to determine column count.
            Row header = s.createRow(0);
            header.createCell(0).setCellValue("H1");
            header.createCell(1).setCellValue("H2");
            header.createCell(2).setCellValue("H3");
            header.createCell(3).setCellValue("H4");

            Row r0 = s.createRow(1);
            // Numeric
            r0.createCell(0).setCellValue(10.0);
            // String with comma
            r0.createCell(1).setCellValue("20,5");
            // Formula (evaluate before save to populate cached value)
            r0.createCell(2).setCellFormula("1+1");
            // Empty string to keep column count
            r0.createCell(3).setCellValue("");

            // Evaluate formulas to populate cached results used by reader
            var evaluator = wb.getCreationHelper().createFormulaEvaluator();
            evaluator.evaluateAll();

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }

        var matrix = timeSeriesReader.readFromXlsx(file, "2030");
        assertEquals(8760, matrix.getRowCount());
        assertEquals(4, matrix.columns().size());
        assertEquals(10.0, matrix.columns().get(0).values()[0]);
        assertEquals(20.5, matrix.columns().get(1).values()[0]);
        assertEquals(2.0, matrix.columns().get(2).values()[0]);
        assertEquals(0.0, matrix.columns().get(3).values()[0]);
    }

    @Test
    void readFromXlsx_shouldDefaultToFirstSheetWhenHorizonBlank(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("first.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s1 = wb.createSheet("first");
            // Header row to be skipped
            Row header = s1.createRow(0);
            header.createCell(0).setCellValue("Header");

            Row r0 = s1.createRow(1);
            r0.createCell(0).setCellValue(7.0);
            wb.createSheet("second");
            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        var matrix = timeSeriesReader.readFromXlsx(file, null);
        assertEquals(7.0, matrix.columns().get(0).values()[0]);
    }

    @Test
    void readFromXlsx_shouldThrowBusinessExceptionWhenHorizonMissing(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("missingSheet.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet("other");
            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }

        var ex = assertThrows(BusinessException.class,
                () -> timeSeriesReader.readFromXlsx(file, "2031"));
        assertEquals("Horizon {0} does not exist in file: {1}", ex.getMessage());
        assertEquals(2, ex.getErrorMessageArguments().size());
        assertEquals("2031", ex.getErrorMessageArguments().get(0));
    }

    @Test
    void readFromXlsx_shouldThrowTechnicalExceptionWhenNoSheets(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("noSheets.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            wb.createSheet("toRemove");
            wb.removeSheetAt(0);
            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        var ex = assertThrows(TechnicalException.class, () -> timeSeriesReader.readFromXlsx(file, null));
        assertEquals("Excel file has no sheets", ex.getMessage());
    }

    @Test
    void readFromXlsx_shouldThrowTechnicalExceptionWhenSheetEmpty(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("emptySheet.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet("2030"); // no rows
            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }
        var ex = assertThrows(TechnicalException.class, () -> timeSeriesReader.readFromXlsx(file, "2030"));
        assertEquals("Excel sheet is empty", ex.getMessage());
    }

    @Test
    void readFromXlsx_shouldThrowTechnicalExceptionWhenFileNotFound(@TempDir Path tempDir) {
        Path file = tempDir.resolve("notfound.xlsx");
        var ex = assertThrows(TechnicalException.class, () -> timeSeriesReader.readFromXlsx(file, "2030"));
        assertTrue(ex.getMessage().startsWith("File not found:"));
    }
}
