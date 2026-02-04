/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.rte_france.antares.datamanager_back.util.timeseries_manager;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;

import java.util.List;
import java.util.Objects;

/**
 * Utility class for reading time series data from text or Excel and producing a matrix
 */
public final class TimeSeriesReader {
  private static final int ROW_COUNT = 8760;

  public TimeSeriesMatrix readFromTxt(Path filePath) throws IOException {
    Objects.requireNonNull(filePath);

    try (var lines = Files.lines(filePath)) {
      var iterator = lines.iterator();
      if (!iterator.hasNext()) {
        throw TechnicalException.builder().message("File is empty").build();
      }

      var firstLine = iterator.next();
      var columnCount = firstLine.split("\\s+").length;
      var data = new double[columnCount][ROW_COUNT];

      fillDataList(firstLine, iterator, data);

      var columns = new ArrayList<TimeSeriesMatrixColumn>(data.length);
      for (int j = 0; j < data.length; j++) {
        columns.add(new TimeSeriesMatrixColumn("Column" + j, data[j]));
      }

      return new TimeSeriesMatrix(columns);
    }
  }

  /**
   * Reads a time series matrix from the specified sheet of an Excel file (.xlsx).
   * Each column in the sheet is interpreted as a series, and up to 8760 rows are read.
   * Non-numeric and blank cells are treated as 0.0; string numbers with comma are supported.
   */
  public TimeSeriesMatrix readFromXlsx(Path xlsxPath, String horizon) throws IOException {
    Objects.requireNonNull(xlsxPath);

    if (!Files.exists(xlsxPath)) {
      throw TechnicalException.builder().message("File not found: " + xlsxPath).build();
    }
    try (InputStream in = Files.newInputStream(xlsxPath); Workbook wb = WorkbookFactory.create(in)) {
      // Check if the workbook has sheets
      if (wb.getNumberOfSheets() == 0) {
        throw TechnicalException.builder().message("Excel file has no sheets").build();
      }

      // Determine the sheet to work with
      Sheet sheet = getSheet(wb, horizon, xlsxPath);
      if (sheet == null) {
        throw TechnicalException.builder().message("Sheet not found").build();
      }

      // Find the first non-empty row
      Row firstRow = findFirstNonEmptyRow(sheet);
      if (firstRow == null) {
        throw TechnicalException.builder().message("Excel sheet is empty").build();
      }

      // Determine column count and validate
      int columnCount = Math.max(0, firstRow.getLastCellNum());
      if (columnCount == 0) {
        throw TechnicalException.builder().message("Excel sheet has no columns").build();
      }

      // Load data into a matrix
      double[][] data = loadData(sheet, columnCount);

      // Create TimeSeriesMatrix
      var columns = new ArrayList<TimeSeriesMatrixColumn>(columnCount);
      for (int c = 0; c < columnCount; c++) {
        columns.add(new TimeSeriesMatrixColumn("Column" + c, data[c]));
      }

      return new TimeSeriesMatrix(columns);

    } catch (IOException | RuntimeException e) {
      throw e; // Re-throw IOException or RuntimeException
    } catch (Exception e) {
      throw new IOException(e); // Convert other exceptions into IOException
    }
  }

  // Helper method to retrieve sheet based on horizon
  private Sheet getSheet(Workbook wb, String horizon, Path xlsxPath) {
    if (horizon != null && !horizon.isBlank()) {
      Sheet sheet = wb.getSheet(horizon);
      if (sheet == null) {
        throw  BusinessException.builder()
               .message("Horizon {0} does not exist in file: {1}")
                .errorMessageArguments(List.of(horizon,xlsxPath.getFileName().toString()))
                  .httpStatus(HttpStatus.BAD_REQUEST)
                  .build();
      }
      return sheet;
    } else {
      return wb.getSheetAt(0);
    }
  }

  // Helper method to find the first non-empty row
  private Row findFirstNonEmptyRow(Sheet sheet) {
    int firstRowNum = sheet.getFirstRowNum();
    int lastRowNum = sheet.getLastRowNum();
    for (int r = firstRowNum; r <= lastRowNum; r++) {
      Row row = sheet.getRow(r);
      if (row != null && row.getLastCellNum() > 0) {
        return row;
      }
    }
    return null;
  }

  /**
   *
   * @param sheet
   * @param columnCount
   * @return it will SKIP the first row containing COLUMN NAMES
   * if REUSE we have to verify if ok for others files
   */
  private double[][] loadData(Sheet sheet, int columnCount) {
    double[][] data = new double[columnCount][ROW_COUNT];
    int rowIndex = 0;
    boolean firstRowSkipped = false;
    for (int r = sheet.getFirstRowNum(); r <= sheet.getLastRowNum() && rowIndex < ROW_COUNT; r++) {
      Row row = sheet.getRow(r);
      if (row == null || row.getLastCellNum() <= 0) {
        continue;
      }
      if (!firstRowSkipped) {
        firstRowSkipped = true;
        continue;
      }
      for (int c = 0; c < columnCount; c++) {
        data[c][rowIndex] = readNumericCell(row, c);
      }
      rowIndex++;
    }
    return data;
  }

  private static double readNumericCell(Row row, int columnIndex) {
    if (row == null) return 0.0;
    Cell cell = row.getCell(columnIndex);
    if (cell == null) return 0.0;
    CellType type = cell.getCellType();
    return switch (type) {
      case NUMERIC -> cell.getNumericCellValue();
      case STRING -> parseStringNumber(cell.getStringCellValue());
      case FORMULA -> switch (cell.getCachedFormulaResultType()) {
        case NUMERIC -> cell.getNumericCellValue();
        case STRING -> parseStringNumber(cell.getStringCellValue());
        default -> 0.0;
      };
      default -> 0.0;
    };
  }

  private static double parseStringNumber(String s) {
    if (s == null) return 0.0;
    s = s.trim();
    if (s.isEmpty()) return 0.0;
    try {
      return Double.parseDouble(s.replace(',', '.'));
    } catch (NumberFormatException e) {
      return 0.0;
    }
  }

  private static void fillDataList(String firstLine, Iterator<String> iterator, double[][] data) {
    var rowIndex = 0;
    while ((firstLine != null || iterator.hasNext()) && rowIndex < ROW_COUNT) {
      String[] values;
      if (firstLine != null) {
        values = firstLine.split("\\s+");
        firstLine = null;
      } else {
        values = iterator.next().split("\\s+");
      }
      for (var j = 0; j < values.length && j < data.length; j++) {
        data[j][rowIndex] = Double.parseDouble(values[j].replace(',', '.'));
      }
      rowIndex++;
    }
  }
}