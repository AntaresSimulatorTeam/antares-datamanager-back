package com.rte_france.antares.datamanager_back.service.nuclear.impl;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Helper class to create valid Excel files for testing
 */
public class NuclearTestDataBuilder {

    /**
     * Create a valid parameters Excel file with modulation data
     */
    public static void createValidParametersFile(Path filePath, String trajectoryName, String horizon) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(trajectoryName);

            // Create header row with horizon year
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Parameter");
            String horizonYear = horizon.split("-")[1];
            headerRow.createCell(1).setCellValue(horizonYear);

            // Add the three modulation types with values
            addParameterRow(sheet, 1, "nucFR_modul_hourly", 0.75);
            addParameterRow(sheet, 2, "nucFR_modul_daily", 0.85);
            addParameterRow(sheet, 3, "nucFR_modul_weekly", 0.80);

            // Write to file
            try (OutputStream outputStream = Files.newOutputStream(filePath)) {
                workbook.write(outputStream);
            }
        }
    }

    /**
     * Create a valid parameters Excel file without the required modulation parameters
     */
    public static void createParametersFileWithoutModulation(Path filePath, String trajectoryName, String horizon) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(trajectoryName);

            // Create header row
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Parameter");
            String horizonYear = horizon.split("-")[1];
            headerRow.createCell(1).setCellValue(horizonYear);

            // Add a non-modulation parameter
            addParameterRow(sheet, 1, "some_other_parameter", 0.50);

            // Write to file
            try (OutputStream outputStream = Files.newOutputStream(filePath)) {
                workbook.write(outputStream);
            }
        }
    }

    /**
     * Create a valid parameters Excel file with header but no horizon column
     */
    public static void createParametersFileWithoutHorizonColumn(Path filePath, String trajectoryName) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(trajectoryName);

            // Create header row without the required horizon year
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Parameter");
            headerRow.createCell(1).setCellValue("2020");

            // Add modulation parameters but for different year
            addParameterRow(sheet, 1, "nucFR_modul_hourly", 0.75);
            addParameterRow(sheet, 2, "nucFR_modul_daily", 0.85);
            addParameterRow(sheet, 3, "nucFR_modul_weekly", 0.80);

            // Write to file
            try (OutputStream outputStream = Files.newOutputStream(filePath)) {
                workbook.write(outputStream);
            }
        }
    }

    /**
     * Create a parameters Excel file without header row
     */
    public static void createParametersFileWithoutHeader(Path filePath, String trajectoryName) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(trajectoryName);

            // Add data rows without header
            addParameterRow(sheet, 0, "nucFR_modul_hourly", 0.75);
            addParameterRow(sheet, 1, "nucFR_modul_daily", 0.85);

            // Write to file
            try (OutputStream outputStream = Files.newOutputStream(filePath)) {
                workbook.write(outputStream);
            }
        }
    }

    /**
     * Create a valid time series Excel file with required sheet
     */
    public static void createValidTimeSeriesFile(Path filePath, String horizon) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            String horizonYear = horizon.split("-")[1];
            Sheet sheet = workbook.createSheet(horizonYear);

            // Create a minimal valid sheet with data
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue("Area");
            row.createCell(1).setCellValue(100.0);

            // Write to file
            try (OutputStream outputStream = Files.newOutputStream(filePath)) {
                workbook.write(outputStream);
            }
        }
    }

    /**
     * Create a time series Excel file without the required sheet
     */
    public static void createTimeSeriesFileWithoutSheet(Path filePath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            // Create a sheet with wrong name
            Sheet sheet = workbook.createSheet("wrong_sheet");
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue("Data");

            // Write to file
            try (OutputStream outputStream = Files.newOutputStream(filePath)) {
                workbook.write(outputStream);
            }
        }
    }

    /**
     * Add a parameter row to the sheet
     */
    private static void addParameterRow(Sheet sheet, int rowIndex, String parameterName, double value) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(parameterName);
        row.createCell(1).setCellValue(value);
    }
}

