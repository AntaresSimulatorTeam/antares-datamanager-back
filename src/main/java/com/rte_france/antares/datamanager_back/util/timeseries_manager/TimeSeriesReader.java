/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.rte_france.antares.datamanager_back.util.timeseries_manager;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.http.HttpStatus;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.parsers.ParserConfigurationException;

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
      String separator = firstLine.contains(";") ? ";" : "\\s+";

      var headerValues = firstLine.split(separator);
      var columnCount = headerValues.length;
      var data = new double[columnCount][ROW_COUNT];

      fillDataList(iterator, data, separator);

      var columns = new ArrayList<TimeSeriesMatrixColumn>(data.length);
      for (int j = 0; j < data.length; j++) {
        String colName = headerValues[j].trim();
        columns.add(new TimeSeriesMatrixColumn(colName.isEmpty() ? "Column" + j : colName, data[j]));
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
        String columnName = getHeaderValue(firstRow.getCell(c));
        columns.add(new TimeSeriesMatrixColumn(columnName != null ? columnName : "Column" + c, data[c]));
      }

      return new TimeSeriesMatrix(columns);

    } catch (IOException | RuntimeException e) {
      throw e; // Re-throw IOException or RuntimeException
    } catch (Exception e) {
      throw new IOException(e); // Convert other exceptions into IOException
    }
  }

  /**
   * Reads only the requested columns from an Excel sheet (POI SAX) for large workboooks
   */
  public TimeSeriesMatrix readSelectedColumnsFromXlsx(Path xlsxPath, String horizon, Set<String> requiredColumnNames)
      throws IOException {
    Objects.requireNonNull(xlsxPath);

    Set<String> requiredLower = requiredColumnNames == null
        ? Set.of()
        : requiredColumnNames.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(s -> s.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());

    if (requiredLower.isEmpty()) {
      return new TimeSeriesMatrix(List.of());
    }

    if (!Files.exists(xlsxPath)) {
      throw TechnicalException.builder().message("File not found: " + xlsxPath).build();
    }

    try (OPCPackage pkg = OPCPackage.open(xlsxPath.toFile(), PackageAccess.READ)) {
      XSSFReader reader = new XSSFReader(pkg);
      StylesTable styles = reader.getStylesTable();
      ReadOnlySharedStringsTable sharedStrings = new ReadOnlySharedStringsTable(pkg);

      try (InputStream sheetInput = openSheetInputStream(reader, horizon, xlsxPath)) {
        SelectedColumnsSheetHandler handler = new SelectedColumnsSheetHandler(requiredLower);
        XMLReader parser = XMLHelper.newXMLReader();
        parser.setContentHandler(new XSSFSheetXMLHandler(
            styles,
            null,
            sharedStrings,
            handler,
            new DataFormatter(),
            false
        ));
        parser.parse(new InputSource(sheetInput));
        return handler.toMatrix();
      }
    } catch (OpenXML4JException | SAXException | ParserConfigurationException e) {
      throw new IOException(e);
    }
  }

  private InputStream openSheetInputStream(XSSFReader reader, String horizon, Path xlsxPath) throws IOException, OpenXML4JException {
    XSSFReader.SheetIterator iterator = (XSSFReader.SheetIterator) reader.getSheetsData();
    if (!iterator.hasNext()) {
      throw TechnicalException.builder().message("Excel file has no sheets").build();
    }

    if (horizon == null || horizon.isBlank()) {
      return iterator.next();
    }

    while (iterator.hasNext()) {
      InputStream candidate = iterator.next();
      String sheetName = iterator.getSheetName();
      if (horizon.equals(sheetName)) {
        return candidate;
      }
      candidate.close();
    }

    throw BusinessException.builder()
        .message("Horizon {0} does not exist in file: {1}")
        .errorMessageArguments(List.of(horizon, xlsxPath.getFileName().toString()))
        .httpStatus(HttpStatus.BAD_REQUEST)
        .build();
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
   * Loads numeric data rows into a column-major matrix.
   *
   * @param sheet the sheet to read
   * @param columnCount number of columns to load
   * @return a matrix where each column contains up to {@link #ROW_COUNT} values
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

  /**
   * Extracts header value based on a cell type
   */
  private static String getHeaderValue(Cell cell) {
    if (cell == null) return null;
    return switch (cell.getCellType()) {
      case STRING -> cell.getStringCellValue();
      case NUMERIC -> String.valueOf(cell.getNumericCellValue());
      default -> null;
    };
  }

  /**
   * Parses string to double, handling null/empty/format errors
   */
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

  private static void fillDataList(Iterator<String> iterator, double[][] data, String separator) {
    var rowIndex = 0;
    while (iterator.hasNext() && rowIndex < ROW_COUNT) {
      String[] values = iterator.next().split(separator);
      for (var j = 0; j < values.length && j < data.length; j++) {
        data[j][rowIndex] = parseStringNumber(values[j]);
      }
      rowIndex++;
    }
  }

  private static final class SelectedColumnsSheetHandler implements XSSFSheetXMLHandler.SheetContentsHandler {
    private final Set<String> requiredLower;
    private final Map<Integer, String> selectedHeaders = new LinkedHashMap<>();
    private final Map<Integer, double[]> dataByColumnIndex = new HashMap<>();
    private final Map<Integer, String> currentRowValues = new HashMap<>();
    private boolean currentRowHasCells;
    private boolean headerInitialized;
    private int dataRowIndex;

    private SelectedColumnsSheetHandler(Set<String> requiredLower) {
      this.requiredLower = requiredLower;
    }

    @Override
    public void startRow(int rowNum) {
      currentRowValues.clear();
      currentRowHasCells = false;
    }

    @Override
    public void endRow(int rowNum) {
      if (!currentRowHasCells) {
        return;
      }

      if (!headerInitialized) {
        initializeSelectedHeaders();
        headerInitialized = true;
        return;
      }

      if (dataRowIndex >= ROW_COUNT) {
        return;
      }

      for (Map.Entry<Integer, String> entry : selectedHeaders.entrySet()) {
        String value = currentRowValues.get(entry.getKey());
        dataByColumnIndex.get(entry.getKey())[dataRowIndex] = parseStringNumber(value);
      }
      dataRowIndex++;
    }

    @Override
    public void cell(String cellReference, String formattedValue, XSSFComment comment) {
      if (cellReference == null) {
        return;
      }

      int colIndex = new CellReference(cellReference).getCol();
      currentRowValues.put(colIndex, formattedValue);
      currentRowHasCells = true;
    }

    @Override
    public void endSheet() {
      // No-op
    }

    private void initializeSelectedHeaders() {
      for (Map.Entry<Integer, String> entry : currentRowValues.entrySet()) {
        String header = entry.getValue() != null ? entry.getValue().trim() : "";
        if (header.isEmpty()) {
          continue;
        }
        if (requiredLower.contains(header.toLowerCase(Locale.ROOT))) {
          selectedHeaders.put(entry.getKey(), header);
          dataByColumnIndex.put(entry.getKey(), new double[ROW_COUNT]);
        }
      }
    }

    private TimeSeriesMatrix toMatrix() {
      if (!headerInitialized) {
        throw TechnicalException.builder().message("Excel sheet is empty").build();
      }

      var columns = new ArrayList<TimeSeriesMatrixColumn>(selectedHeaders.size());
      for (Map.Entry<Integer, String> entry : selectedHeaders.entrySet()) {
        columns.add(new TimeSeriesMatrixColumn(entry.getValue(), dataByColumnIndex.get(entry.getKey())));
      }
      return new TimeSeriesMatrix(columns);
    }
  }
}