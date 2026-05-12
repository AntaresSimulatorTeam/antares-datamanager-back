package com.rte_france.antares.datamanager_back.util;

import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
public class CreateExcelTestUtil {
    public static Path createExcelFile(@TempDir Path tempDir, String fileName, String sheetName, List<String> headers, List<List<?>> rows) throws IOException {
        Path filePath = tempDir.resolve(fileName);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(sheetName);


            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                headerRow.createCell(i).setCellValue(headers.get(i));
            }


            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                List<?> rowData = rows.get(rowIndex);
                for (int colIndex = 0; colIndex < rowData.size(); colIndex++) {
                    Cell cell = row.createCell(colIndex);
                    Object value = rowData.get(colIndex);


                    if (value instanceof String) {
                        cell.setCellValue((String) value);
                    } else if (value instanceof Integer) {
                        cell.setCellValue((Integer) value);
                    } else if (value instanceof Double) {
                        cell.setCellValue((Double) value);
                    } else if (value instanceof Boolean) {
                        cell.setCellValue((Boolean) value);
                    } else if (value != null) {
                        cell.setCellValue(value.toString());
                    }
                }
            }

            try (var outputStream = Files.newOutputStream(filePath)) {
                workbook.write(outputStream);
            }
        }

        return filePath;
    }

    public static Path createExcelFileWithTwoSheets(@TempDir Path tempDir, String fileName, List<String> sheetNames, List<List<String>> headers, List<List<List<?>>> rows) throws IOException {
        if (sheetNames.size() != 2 || headers.size() != 2 || rows.size() != 2) {
            throw  TechnicalException.builder().message("You must provide exactly two sheet names, headers, and rows.").build();
        }

        Path filePath = tempDir.resolve(fileName);

        try (Workbook workbook = new XSSFWorkbook()) {
            // Create two sheets
            for (int i = 0; i < 2; i++) {
                Sheet sheet = workbook.createSheet(sheetNames.get(i));

                // Create header row for each sheet
                Row headerRow = sheet.createRow(0);
                for (int j = 0; j < headers.get(i).size(); j++) {
                    headerRow.createCell(j).setCellValue(headers.get(i).get(j));
                }

                // Create data rows for each sheet
                List<List<?>> sheetRows = rows.get(i);
                for (int rowIndex = 0; rowIndex < sheetRows.size(); rowIndex++) {
                    Row row = sheet.createRow(rowIndex + 1);
                    List<?> rowData = sheetRows.get(rowIndex);
                    for (int colIndex = 0; colIndex < rowData.size(); colIndex++) {
                        Cell cell = row.createCell(colIndex);
                        Object value = rowData.get(colIndex);

                        if (value instanceof String) {
                            cell.setCellValue((String) value);
                        } else if (value instanceof Integer) {
                            cell.setCellValue((Integer) value);
                        } else if (value instanceof Double) {
                            cell.setCellValue((Double) value);
                        } else if (value instanceof Boolean) {
                            cell.setCellValue((Boolean) value);
                        } else if (value != null) {
                            cell.setCellValue(value.toString());
                        }
                    }
                }
            }

            try (var outputStream = Files.newOutputStream(filePath)) {
                workbook.write(outputStream);
            }
        }

        return filePath;
    }

    public static void createMockCsvFile(Path directory, String fileName) throws IOException {
        Path csvFile = directory.resolve(fileName);
        Files.writeString(csvFile, "timestamp,value\n2030-01-01,0.5\n2030-01-02,0.6\n");
    }
}