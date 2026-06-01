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
import java.util.Set;

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
        assertEquals(1, matrix.getRowCount());
        assertEquals(2, matrix.columns().size());
        // If first row is ignored, the first data row should be "10.0 20.0"
        assertEquals(10.0, matrix.columns().get(0).values()[0]);
        assertEquals(20.0, matrix.columns().get(1).values()[0]);
    }

    @Test
    void readFromTxt_shouldReadCorrectly(@TempDir Path tempDir) throws IOException {
        var filePath = tempDir.resolve("timeseries.txt");
        Files.createFile(filePath);
        Files.writeString(filePath, "Col1 Col2 Col3\n1.0 2.0 3.0\n4.0 5.0 6.0\n7.0 8.0 9.0");
        var matrix = timeSeriesReader.readFromTxt(filePath);

        assertEquals(3, matrix.getRowCount());
        assertEquals(3, matrix.columns().size());
        assertEquals("Col1", matrix.columns().get(0).name());
        assertArrayEquals(new double[]{1.0, 4.0, 7.0}, Arrays.copyOf(matrix.columns().get(0).values(), 3));
        assertArrayEquals(new double[]{3.0, 6.0, 9.0}, Arrays.copyOf(matrix.columns().get(2).values(), 3));
    }

    @Test
    void readFromTxt_shouldReadWithSemicolonSeparator(@TempDir Path tempDir) throws IOException {
        var filePath = tempDir.resolve("timeseries.csv");
        Files.createFile(filePath);
        Files.writeString(filePath, "date;AT;BE;CH\n2023-01-01;1.0;2.0;3.0\n2023-01-02;4.0;5.0;6.0");
        var matrix = timeSeriesReader.readFromTxt(filePath);

        assertEquals(2, matrix.getRowCount());
        assertEquals(4, matrix.columns().size());
        assertEquals("date", matrix.columns().get(0).name());
        assertEquals("AT", matrix.columns().get(1).name());
        // date column should have 0.0 because it's non-numeric
        assertEquals(0.0, matrix.columns().get(0).values()[0]);
        assertEquals(1.0, matrix.columns().get(1).values()[0]);
        assertEquals(2.0, matrix.columns().get(2).values()[0]);
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
        assertEquals(1, matrix.getRowCount());
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
        assertEquals(7.0, matrix.columns().getFirst().values()[0]);
    }

    @Test
    void readFromTxt_noHeader_shouldReadCorrectly(@TempDir Path tempDir) throws IOException {
        var filePath = tempDir.resolve("timeseries_noheader.txt");
        Files.createFile(filePath);
        Files.writeString(filePath, "1.0 2.0 3.0\n4.0 5.0 6.0\n7.0 8.0 9.0");
        var matrix = timeSeriesReader.readFromTxt(filePath, false);

        assertEquals(3, matrix.getRowCount());
        assertEquals(3, matrix.columns().size());
        assertEquals("Column0", matrix.columns().get(0).name());
        assertEquals("Column1", matrix.columns().get(1).name());
        assertEquals("Column2", matrix.columns().get(2).name());
        assertArrayEquals(new double[]{1.0, 4.0, 7.0}, Arrays.copyOf(matrix.columns().get(0).values(), 3));
        assertArrayEquals(new double[]{3.0, 6.0, 9.0}, Arrays.copyOf(matrix.columns().get(2).values(), 3));
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
        assertEquals("2031", ex.getErrorMessageArguments().getFirst());
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

    @Test
    void readSelectedColumnsFromXlsx_shouldOnlyReturnRequestedColumns(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("selected.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("2030");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("daily_min_fr");
            header.createCell(1).setCellValue("night_min_fr");
            header.createCell(2).setCellValue("other");

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(1.0);
            row.createCell(1).setCellValue(2.0);
            row.createCell(2).setCellValue(3.0);

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }

        TimeSeriesMatrix matrix = timeSeriesReader.readSelectedColumnsFromXlsx(
                file,
                "2030",
                Set.of("night_min_fr")
        );

        assertEquals(1, matrix.columns().size());
        assertEquals("night_min_fr", matrix.columns().getFirst().name());
        assertEquals(2.0, matrix.columns().getFirst().values()[0]);
    }

    @Test
    void readSelectedColumnsFromXlsx_shouldReturnCorrectNumberOfRowsWhenFewerThan8760(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("fewerRows.xlsx");
        int rowCount = 366;
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("2030");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("FR");

            for (int i = 1; i <= rowCount; i++) {
                Row row = sheet.createRow(i);
                row.createCell(0).setCellValue((double) i);
            }

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }

        TimeSeriesMatrix matrix = timeSeriesReader.readSelectedColumnsFromXlsx(
                file,
                "2030",
                Set.of("FR")
        );

        assertEquals(1, matrix.columns().size());
        assertEquals(rowCount, matrix.columns().getFirst().values().length);
        assertEquals(1.0, matrix.columns().getFirst().values()[0]);
        assertEquals((double) rowCount, matrix.columns().getFirst().values()[rowCount - 1]);
    }

    @Test
    void readSelectedColumnsFromXlsx_shouldThrowBusinessExceptionWhenHorizonMissing(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("selected-missing-sheet.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet("other");
            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> timeSeriesReader.readSelectedColumnsFromXlsx(file, "2030", Set.of("daily_min_fr"))
        );

        assertEquals("Horizon {0} does not exist in file: {1}", ex.getMessage());
        assertEquals("2030", ex.getErrorMessageArguments().getFirst());
    }
}
