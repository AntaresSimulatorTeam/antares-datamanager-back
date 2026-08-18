package com.rte_france.antares.datamanager_back.util.timeseries_manager;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NuclearTimeSeriesReaderPrecisionTest {

    @Test
    void readFromXlsx_shouldRoundToTwoDecimals(@TempDir Path tempDir) throws Exception {
        NuclearTimeSeriesReader reader = new NuclearTimeSeriesReader();
        Path file = tempDir.resolve("rounding_test_nuclear.xlsx");

        double valueManyDecimals = 1234.5678;
        double expectedRoundedValue = 1234.57;

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("2030");
            Row header = s.createRow(0);
            header.createCell(0).setCellValue("Value");

            Row r1 = s.createRow(1);
            r1.createCell(0).setCellValue(valueManyDecimals);

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }

        var matrix = reader.readFromXlsx(file, "2030", true);

        assertEquals(1, matrix.getRowCount());
        System.out.println("[DEBUG_LOG] Nuclear Value read: " + matrix.columns().get(0).values()[0]);
        assertEquals(expectedRoundedValue, matrix.columns().get(0).values()[0], "Value should have 2 decimals");
    }

    @Test
    void readFromXlsx_shouldKeepTwoDecimals(@TempDir Path tempDir) throws Exception {
        NuclearTimeSeriesReader reader = new NuclearTimeSeriesReader();
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

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }

        var matrix = reader.readFromXlsx(file, "2030", true);


        assertEquals(1, matrix.getRowCount());
        System.out.println("[DEBUG_LOG] Nuclear Value read: " + matrix.columns().get(0).values()[0]);
        assertEquals(valueWithTwoDecimals, matrix.columns().get(0).values()[0], "2 decimals should be kept");
    }
}
