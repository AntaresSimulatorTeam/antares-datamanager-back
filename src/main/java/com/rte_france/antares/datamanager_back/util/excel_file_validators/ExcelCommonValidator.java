package com.rte_france.antares.datamanager_back.util.excel_file_validators;

import com.rte_france.antares.datamanager_back.exception.TechnicalAntaresDataMangerException;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.ExcelFileType;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
@Slf4j
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
            if (sheet == null) {
                throw new TechnicalAntaresDataMangerException("File '" + path.getFileName() + "' does not contain the expected sheet: '" + horizon + "'.");
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new TechnicalAntaresDataMangerException("File '" + path.getFileName() + "' does not contain a valid header row.");
            }


            List<String> actualColumns = new ArrayList<>();
            for (Cell cell : headerRow) {
                if (cell.getCellType() == CellType.STRING && !cell.getStringCellValue().trim().isEmpty()) {
                    actualColumns.add(cell.getStringCellValue().trim());
                }
            }

            List<String> missingColumns = fileType.checkColumnNames(actualColumns);

            List<String> expectedColumns = fileType.getColumnNames();
            List<String> invalidColumns = actualColumns.stream()
                    .filter(actual -> !expectedColumns.contains(ExcelFileType.normalizeColumnName(actual)))
                    .toList();

            StringBuilder errorMessage = new StringBuilder("Error in sheet '")
                    .append(horizon)
                    .append("' in file: ")
                    .append(path.getFileName())
                    .append(".");

            boolean errorOccurred = false;


            if (!missingColumns.isEmpty()) {
                errorMessage.append(" Missing columns: ").append(String.join(", ", missingColumns));
                errorOccurred = true;
            }


            if (!invalidColumns.isEmpty()) {
                if (errorOccurred) {
                    errorMessage.append(". ");
                }
                errorMessage.append("Invalid columns: ").append(String.join(", ", invalidColumns));
            }

            if (errorOccurred) {
                throw new TechnicalAntaresDataMangerException(errorMessage.toString());
            }

            checkAllRowsHaveValues(sheet, fileType.getColumnCount(), path, horizon);

        } catch (IOException e) {
            throw new TechnicalAntaresDataMangerException("Error reading file '" + path.getFileName() + "': " + e.getMessage());
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
     * @param booleanColumns booleans columns must be TRUE or FALSE
     */
    static void checkBooleanColumns(Sheet sheet, Path path, String horizon, List<String> booleanColumns) {
        Map<String, Integer> columnIndexes = booleanColumns.stream()
                .collect(Collectors.toMap(Function.identity(), column -> ExcelCommonValidator.findColumnIndex(sheet, column, path, horizon)));

        List<String> invalidCells = IntStream.rangeClosed(1, sheet.getLastRowNum())
                .mapToObj(sheet::getRow)
                .filter(Objects::nonNull)
                .flatMap(row -> columnIndexes.entrySet().stream()
                        .map(entry -> Map.entry(entry.getKey(), Map.entry(row.getRowNum() + 1, row.getCell(entry.getValue(), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))))
                        .filter(entry -> !isValidBoolean(entry.getValue().getValue()))
                        .map(entry -> String.format("Row %d, Column '%s'", entry.getValue().getKey(), entry.getKey())))
                .toList();

        if (!invalidCells.isEmpty()) {
            throw new TechnicalAntaresDataMangerException(String.format(
                    "Invalid boolean values in sheet '%s' in file: %s - must be true/false. Locations: %s",
                    horizon, path.getFileName(), String.join("; ", invalidCells)));
        }
    }

    /**
     * @param sheet to verify strings
     * @param path to file
     * @param horizon sheet name
     * @param columnName where we expect values to be strings an throw error if a number is found
     */
    public static void checkStringColumns(Sheet sheet, Path path, String horizon, String columnName) {
        int columnIndex = findColumnIndex(sheet, columnName, path, horizon);

        List<String> invalidCells = IntStream.rangeClosed(1, sheet.getLastRowNum())
                .mapToObj(sheet::getRow)
                .filter(Objects::nonNull)
                .map(row -> Map.entry(row.getRowNum() + 1, row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)))
                .filter(entry -> {
                    Cell cell = entry.getValue();
                    if (cell == null) return false;

                    if (cell.getCellType() == CellType.NUMERIC) {
                        return true;
                    }


                    if (cell.getCellType() == CellType.STRING) {
                        String cellValue = cell.getStringCellValue().trim();
                        return cellValue.matches("\\d+"); // Invalid if it contains only digits
                    }

                    return false;
                })
                .map(entry -> {
                    String actualValue = entry.getValue().toString();
                    return "Waiting for string value at row " + entry.getKey() + ", Column " + (columnIndex + 1) +
                            " (Invalid value: '" + actualValue + "', numbers are not allowed)";
                })
                .toList();

        if (!invalidCells.isEmpty()) {
            throw new TechnicalAntaresDataMangerException("Column '" + columnName + "' errors in sheet '" + horizon + "' in file: " + path.getFileName() +
                    ". Locations: " + String.join(", ", invalidCells));
        }
    }

    private static boolean isValidBoolean(Cell cell) {
        if (cell == null) {
            return false;
        }
        return switch (cell.getCellType()) {
            case BOOLEAN -> true;
            case STRING -> {
                String value = cell.getStringCellValue().trim().toUpperCase();
                yield "TRUE".equals(value) || "FALSE".equals(value);
            }
            default -> false;
        };

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

    /**
     * @param sheet      to be read in Excel file
     * @param columnName column to be read
     * @param path       trajectory file
     * @param horizon    to make error clearer
     * @param checkSymmetric  true if links rule to be verified (AT-BE = BE-AT)
     * Method to find  duplicated values in a specific column
     */
    static void checkForDuplicateValues(Sheet sheet, String columnName, Path path, String horizon, boolean checkSymmetric) {
        int columnIndex = findColumnIndex(sheet, columnName, path, horizon);
        Map<String, String> seenValues = new HashMap<>();

        sheet.forEach(row -> Optional.ofNullable(row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                .map(ExcelCommonValidator::getCellValueAsString)
                .map(String::trim)
                .filter(cellValue -> !cellValue.isEmpty())
                .ifPresent(cellValue -> {
                    String normalizedValue = checkSymmetric ? normalizeSymmetricValue(cellValue) : cellValue;

                    if (seenValues.containsKey(normalizedValue)) {
                        String firstOccurrence = seenValues.get(normalizedValue);

                        // Avoid showing the same value twice if they are identical
                        if (checkSymmetric && !firstOccurrence.equals(cellValue)) {
                            throw new TechnicalAntaresDataMangerException(
                                    String.format("Duplicate value found in column '%s' in sheet '%s' in file: %s. Values '%s' and '%s' are considered identical.",
                                            columnName, horizon, path.getFileName(), firstOccurrence, cellValue));
                        } else {
                            throw new TechnicalAntaresDataMangerException(
                                    String.format("Duplicate value '%s' found in column '%s' in sheet '%s' in file: %s.",
                                            cellValue, columnName, horizon, path.getFileName()));
                        }
                    }

                    seenValues.put(normalizedValue, cellValue);
                }));
    }



    private static String getCellValueAsString(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((int) cell.getNumericCellValue()); // Convert numeric values to String
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private static String normalizeSymmetricValue(String value) {
        String[] parts = value.split("-");
        Arrays.sort(parts);
        return String.join("-", parts);
    }
}