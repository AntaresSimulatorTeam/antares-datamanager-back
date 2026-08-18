package com.rte_france.antares.datamanager_back.util.timeseries_manager;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Specific reader for Nuclear time series that ensures decimal precision.
 * Uses POI Workbook API to get exact numeric values.
 */
@Component
public class NuclearTimeSeriesReader {
    private static final int MAX_ROWS_PER_YEAR = 8784;
    private static final String COLUMN_PREFIX = "Column";

    public TimeSeriesMatrix readFromXlsx(Path xlsxPath, String horizon, boolean hasHeader) throws IOException {
        Objects.requireNonNull(xlsxPath);
        requireFileExists(xlsxPath);

        try (Workbook workbook = WorkbookFactory.create(xlsxPath.toFile(), null, true)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw TechnicalException.builder().message("Excel file has no sheets").build();
            }
            Sheet sheet = (horizon == null || horizon.isBlank()) ? workbook.getSheetAt(0) : workbook.getSheet(horizon);
            if (sheet == null) {
                throw BusinessException.builder()
                        .message("Horizon {0} does not exist in file: {1}")
                        .errorMessageArguments(List.of(horizon != null ? horizon : "default", xlsxPath.getFileName().toString()))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            int rowCount = sheet.getLastRowNum() + 1;
            if (rowCount == 0) {
                throw TechnicalException.builder().message("Excel sheet is empty").build();
            }

            int startRow = hasHeader ? 1 : 0;
            int numDataRows = Math.min(MAX_ROWS_PER_YEAR, Math.max(0, rowCount - startRow));

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw TechnicalException.builder().message("Excel sheet is empty").build();
            }
            int colCount = headerRow.getLastCellNum();
            List<String> headerNames = new ArrayList<>(colCount);
            for (int i = 0; i < colCount; i++) {
                Cell cell = headerRow.getCell(i);
                String name = null;
                if (hasHeader && cell != null) {
                    name = new DataFormatter().formatCellValue(cell).trim();
                }
                headerNames.add(name != null && !name.isEmpty() ? name : COLUMN_PREFIX + i);
            }

            List<TimeSeriesMatrixColumn> columns = new ArrayList<>(colCount);
            for (int i = 0; i < colCount; i++) {
                double[] values = new double[numDataRows];
                for (int r = 0; r < numDataRows; r++) {
                    Row row = sheet.getRow(r + startRow);
                    if (row != null) {
                        Cell cell = row.getCell(i);
                        values[r] = getNumericCellValue(cell);
                    }
                }
                columns.add(new TimeSeriesMatrixColumn(headerNames.get(i), values));
            }

            return new TimeSeriesMatrix(columns);
        }
    }

    private double getNumericCellValue(Cell cell) {
        if (cell == null) return 0.0;
        double val = 0.0;
        if (cell.getCellType() == CellType.NUMERIC) {
            val = cell.getNumericCellValue();
        } else if (cell.getCellType() == CellType.STRING) {
            val = parseStringNumber(cell.getStringCellValue());
        } else if (cell.getCellType() == CellType.FORMULA) {
            if (cell.getCachedFormulaResultType() == CellType.NUMERIC) {
                val = cell.getNumericCellValue();
            } else if (cell.getCachedFormulaResultType() == CellType.STRING) {
                val = parseStringNumber(cell.getRichStringCellValue().getString());
            }
        }
        return Math.round(val * 100.0) / 100.0;
    }

    private double parseStringNumber(String s) {
        if (s == null) return 0.0;
        s = s.trim();
        if (s.isEmpty()) return 0.0;
        try {
            return Double.parseDouble(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private void requireFileExists(Path xlsxPath) {
        if (!Files.exists(xlsxPath)) {
            throw TechnicalException.builder()
                    .errorMessageArguments(List.of(xlsxPath.toString()))
                    .message("File not found: {0}")
                    .build();
        }
    }
}
