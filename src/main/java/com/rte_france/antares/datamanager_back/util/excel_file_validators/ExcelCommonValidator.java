package com.rte_france.antares.datamanager_back.util.excel_file_validators;

import com.rte_france.antares.datamanager_back.exception.TechnicalAntaresDataMangerException;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.ExcelFileType;
import lombok.Getter;
import org.apache.poi.ss.usermodel.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.IntStream;

@Getter
public class ExcelCommonValidator {


    /**
     * @param path     trajectory file
     * @param fileType to verify columns names using ColumnEnums
     * @param horizon  sheet name to be read
     */
    public static void checkIfColumnsAreValid(Path path, ExcelFileType fileType, String horizon) {
        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheet(horizon);

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new TechnicalAntaresDataMangerException("The file " + path.getFileName() + " does not contain any columns names.");
            }


            List<String> actualColumns = new ArrayList<>();
            int columnCount = 0;

            for (Cell cell : headerRow) {
                if (cell.getCellType() == CellType.STRING && !cell.getStringCellValue().trim().isEmpty()) {
                    columnCount++;
                }
                actualColumns.add(cell.getStringCellValue());
            }
            if (columnCount != fileType.getColumnCount()) {
                throw new TechnicalAntaresDataMangerException("Invalid number of columns in sheet '" + horizon + "': Expected "
                        + fileType.getColumnCount() + ", but found " + columnCount);
            }
            List<String> wrongColumnsName = fileType.checkColumnNames(actualColumns);
            if (!wrongColumnsName.isEmpty()) {
                throw new TechnicalAntaresDataMangerException("Invalid column names in sheet '" + horizon +
                        "' in file: " + path.getFileName() + ". Wrong column name for: " + String.join(", ", wrongColumnsName));
            }

            checkAllRowsHaveValues(sheet, fileType.getColumnCount(), path, horizon);


        } catch (IOException e) {
            throw new TechnicalAntaresDataMangerException("Could not check columns in file: " + e.getMessage());
        }
    }

    /**
     * @param sheet       to be read
     * @param columnCount index of column to check if there is not any empty values
     * @param path        trajectory file
     * @param horizon     to make error clearer
     */
    private static void checkAllRowsHaveValues(Sheet sheet, int columnCount, Path path, String horizon) {
        List<String> emptyCells = IntStream.rangeClosed(1, sheet.getLastRowNum())
                .mapToObj(sheet::getRow)
                .filter(row -> row != null && row.getPhysicalNumberOfCells() > 0)
                .flatMap(row -> IntStream.range(0, columnCount)
                        .mapToObj(colIndex -> Map.entry(row.getRowNum() + 1, colIndex + 1))
                        .filter(entry -> {
                            Cell cell = row.getCell(entry.getValue() - 1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                            return cell == null || cell.getCellType() == CellType.BLANK ||
                                    (cell.getCellType() == CellType.STRING && cell.getStringCellValue().trim().isEmpty());
                        })
                )
                .map(entry -> "Row " + entry.getKey() + ", Column " + entry.getValue())
                .toList();

        if (!emptyCells.isEmpty()) {
            throw new TechnicalAntaresDataMangerException("Empty values found in sheet '" + horizon + "' in file: " + path.getFileName() +
                    ". Locations: " + String.join(", ", emptyCells));
        }
    }

    /**
     * @param sheet   to be read in Excel file
     * @param path    trajectory file
     * @param horizon make error clearer
     * @param column  booleans columns must be TRUE or FALSE
     */
    static void checkBooleanColumn(Sheet sheet, Path path, String horizon, String column) {
        int index = findColumnIndex(sheet, column, path, horizon);

        List<String> invalidRows = IntStream.range(1, sheet.getPhysicalNumberOfRows())
                .mapToObj(sheet::getRow)
                .filter(row -> row != null && row.getPhysicalNumberOfCells() > 0
                        && row.getCell(0) != null && !row.getCell(0).getStringCellValue().isEmpty()) // Skip empty rows
                .filter(row -> {
                    Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    return cell == null || !isValidBoolean(cell);
                })
                .map(row -> "Row " + (row.getRowNum() + 1))
                .toList();

        if (!invalidRows.isEmpty()) {
            throw new TechnicalAntaresDataMangerException(String.format(
                    "Invalid values in column '%s' in sheet '%s' in file: %s - must be boolean (true/false). Locations: %s",
                    column, horizon, path.getFileName(), String.join(", ", invalidRows)));
        }
    }

    public static void checkStringColumns(Sheet sheet, Path path, String horizon, String columnName) {
        int columnIndex = findColumnIndex(sheet, columnName, path, horizon);

        List<String> invalidCells = IntStream.rangeClosed(1, sheet.getLastRowNum())
                .mapToObj(sheet::getRow)
                .filter(Objects::nonNull)
                .map(row -> Map.entry(row.getRowNum() + 1, row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)))
                .filter(entry -> Optional.ofNullable(entry.getValue())
                        .map(cell -> cell.getCellType() != CellType.STRING)
                        .orElse(false))
                .map(entry -> "Row " + entry.getKey() + ", Column " + (columnIndex + 1) +
                        " (Expected STRING, found " + entry.getValue().getCellType() + ")")
                .toList();

        if (!invalidCells.isEmpty()) {
            throw new TechnicalAntaresDataMangerException("Column '" + columnName + "' errors in sheet '" + horizon + "' in file: " + path.getFileName() +
                    ". Locations: " + String.join(", ", invalidCells));
        }
    }

    private static boolean isValidBoolean(Cell cell) {
        if (cell.getCellType() == CellType.BOOLEAN) {
            return true;
        }
        if (cell.getCellType() == CellType.STRING) {
            String value = cell.getStringCellValue().trim().toUpperCase();
            return "TRUE".equals(value) || "FALSE".equals(value);
        }
        return false;
    }

    /**
     * @param sheet      to be read
     * @param columnName column to be read
     * @param path       trajectory file
     * @param horizon    to make error clearer
     * @return column index to be found
     */
    static int findColumnIndex(Sheet sheet, String columnName, Path path, String horizon) {
        Row headerRow = sheet.getRow(0);
        return IntStream.range(0, headerRow.getPhysicalNumberOfCells())
                .filter(i -> columnName.equalsIgnoreCase(headerRow.getCell(i).getStringCellValue()))
                .findFirst()
                .orElseThrow(() -> new TechnicalAntaresDataMangerException(
                        "Column '" + columnName + "' not found in sheet '" + horizon + "' in file: " + path.getFileName()));
    }
}