package com.rte_france.antares.datamanager_back.util.excel_file_validators;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.ExcelFileType;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;

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
                throw BusinessException.builder()
                        .message("File {0} does not contain the expected sheet: {1}")
                        // .antaresErrorCode(antaresErrorCode.DASHBOARD_ERROR_001)
                        .errorMessageArguments(List.of(path.getFileName().toString(), horizon))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw BusinessException.builder()
                        .message("File {0} does not contain a valid header row.")
                        // .antaresErrorCode(antaresErrorCode.DASHBOARD_ERROR_001)
                        .errorMessageArguments(List.of(path.getFileName().toString()))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
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
                throw BusinessException.builder()
                        .message(errorMessage.toString())
                        // .antaresErrorCode(antaresErrorCode.DASHBOARD_ERROR_001)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            checkAllRowsHaveValues(sheet, fileType.getColumnCount(), path, horizon);

        } catch (IOException e) {
            throw TechnicalException.builder()
                    .message("Error reading file {0}: " + path.getFileName())
                    // .antaresErrorCode(antaresErrorCode.DASHBOARD_ERROR_001)
                    .cause(e.getCause())
                    .build();
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
                .filter(row -> row != null && !isRowEmpty(row))
                .flatMap(row -> IntStream.range(0, columnCount)
                        .mapToObj(colIndex -> Map.entry(row.getRowNum() + 1, colIndex + 1))
                        .filter(entry -> {
                            Cell cell = row.getCell(entry.getValue() - 1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                            if (cell == null) return true;

                            return switch (cell.getCellType()) {
                                case STRING -> cell.getStringCellValue().trim().isEmpty();
                                case NUMERIC, BOOLEAN, FORMULA -> false;
                                case BLANK -> true;
                                default -> true;
                            };
                        })
                )
                .map(entry -> "Row " + entry.getKey() + ", Column " + entry.getValue())
                .toList();

        if (!emptyCells.isEmpty()) {
            throw BusinessException.builder()
                    .message("Empty values found in sheet {0} in file:  {1}  Locations: {2}")
                    // .antaresErrorCode(antaresErrorCode.DASHBOARD_ERROR_001)
                    .errorMessageArguments(List.of(horizon, path.getFileName().toString(), String.join(", ", emptyCells)))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    /**
     * Check if a row is fully empty, even if formatted
     */
    private static boolean isRowEmpty(Row row) {
        return row == null || IntStream.range(0, row.getLastCellNum())
                .mapToObj(row::getCell)
                .allMatch(cell -> cell == null ||
                        cell.getCellType() == CellType.BLANK ||
                        (cell.getCellType() == CellType.STRING && cell.getStringCellValue().trim().isEmpty()));

    }


    /**
     * @param sheet          to be read in Excel file
     * @param path           trajectory file
     * @param horizon        make error clearer
     * @param booleanColumns booleans columns must be TRUE or FALSE
     */
    static void checkBooleanColumns(Sheet sheet, Path path, String horizon, List<String> booleanColumns) {
        Map<String, Integer> columnIndexes = booleanColumns.stream()
                .collect(Collectors.toMap(Function.identity(), column -> ExcelCommonValidator.findColumnIndex(sheet, column, path, horizon)));

        List<String> invalidCells = IntStream.rangeClosed(1, sheet.getLastRowNum())
                .mapToObj(sheet::getRow)
                .filter(Objects::nonNull)
                .flatMap(row -> columnIndexes.entrySet().stream()
                        .map(entry -> Map.entry(entry.getKey(),
                                Map.entry(row.getRowNum() + 1, row.getCell(entry.getValue(), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK))))
                        .filter(entry -> !isValidBoolean(entry.getValue().getValue()))
                        .map(entry -> String.format("Row %d, Column '%s'", entry.getValue().getKey(), entry.getKey())))
                .toList();

        if (!invalidCells.isEmpty()) {
            throw BusinessException.builder()
                    .message("Invalid boolean values in sheet {0} in file:{1} - must be true/false. Locations: {2}")
                    // .antaresErrorCode(antaresErrorCode.DASHBOARD_ERROR_001)
                    .errorMessageArguments(List.of(horizon, path.getFileName().toString(), String.join("; ", invalidCells)))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    /**
     * @param sheet      to verify strings
     * @param path       to file
     * @param horizon    sheet name
     * @param columnName where we expect values to be strings and throw error if a number is found
     */
    public static void checkStringColumns(Sheet sheet, Path path, String horizon, String columnName) {
        int columnIndex = findColumnIndex(sheet, columnName, path, horizon);

        List<String> invalidCells = IntStream.rangeClosed(1, sheet.getLastRowNum())
                .mapToObj(sheet::getRow)
                .filter(Objects::nonNull)
                .map(row -> Map.entry(
                        row.getRowNum() + 1,
                        row.getCell(columnIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                ))
                .filter(entry -> {
                    Cell cell = entry.getValue();
                    if (cell == null || cell.getCellType() == CellType.BLANK) return false;

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
                    String actualValue = getCellValue(entry.getValue());
                    return "Waiting for string value at row " + entry.getKey() + ", Column " + (columnIndex + 1) +
                            " (Invalid value: '" + actualValue + "', numbers are not allowed)";
                })
                .toList();

        if (!invalidCells.isEmpty()) {
            throw BusinessException.builder()
                    .message("Column {0} errors in sheet {1} in file:{2}. Locations: {3}")
                    // .antaresErrorCode(antaresErrorCode.DASHBOARD_ERROR_001)
                    .errorMessageArguments(List.of(columnName, horizon, path.getFileName().toString(), String.join("; ", invalidCells)))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private static boolean isValidBoolean(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return true;
        Boolean value = getBooleanCellValue(cell);
        return value != null;
    }

    /**
     * @param cell to check
     * @return boolean value expected and avoid null for formatted cells
     */
    public static Boolean getBooleanCellValue(Cell cell) {
        // If the cell is null or blank, we return null to indicate an invalid or undefined value
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;

        // If the cell contains a boolean value, return it directly
        if (cell.getCellType() == CellType.BOOLEAN) return cell.getBooleanCellValue();

        // If the cell contains a string representation of true/false, parse it
        if (cell.getCellType() == CellType.STRING) {
            String value = cell.getStringCellValue().trim().toUpperCase();
            if ("TRUE".equals(value)) return true;
            if ("FALSE".equals(value)) return false;
        }
        return null;
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
                .orElseThrow(() -> BusinessException.builder()
                        .message("Column {0} not found in sheet {1} in file: {2}")
                        .errorMessageArguments(List.of(columnName, horizon, path.getFileName().toString()))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build());
    }

    /**
     * @param sheet          to be read in Excel file
     * @param columnName     column to be read
     * @param path           trajectory file
     * @param horizon        to make error clearer
     * @param checkSymmetric true if links rule to be verified (AT-BE = BE-AT)
     *                       Method to find  duplicated values in a specific column
     */
    static void checkForDuplicateValues(Sheet sheet, String columnName, Path path, String horizon, boolean checkSymmetric) {
        int columnIndex = findColumnIndex(sheet, columnName, path, horizon);
        Map<String, String> seenValues = new HashMap<>();

        sheet.forEach(row -> Optional.ofNullable(row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                .map(ExcelCommonValidator::getCellValue)
                .map(String::trim)
                .filter(cellValue -> !cellValue.isEmpty())
                .ifPresent(cellValue -> {
                    String normalizedValue = checkSymmetric ? normalizeSymmetricValue(cellValue) : cellValue;

                    if (seenValues.containsKey(normalizedValue)) {
                        String firstOccurrence = seenValues.get(normalizedValue);

                        // Avoid showing the same value twice if they are identical
                        if (checkSymmetric && !firstOccurrence.equals(cellValue)) {
                            throw BusinessException.builder()
                                    .message("Duplicate value found in column {0} in sheet {1} in file: {2} Values {3} and {4} are considered identical.")
                                    .errorMessageArguments(List.of(columnName, horizon, path.getFileName().toString(), firstOccurrence, cellValue))
                                    .httpStatus(HttpStatus.BAD_REQUEST)
                                    .build();
                        } else {
                            throw BusinessException.builder()
                                    .message("Duplicate value {0} found in column {1} in sheet {2} in file: {3}.")
                                    .errorMessageArguments(List.of(cellValue, columnName, horizon, path.getFileName().toString()))
                                    .httpStatus(HttpStatus.BAD_REQUEST)
                                    .build();
                        }
                    }

                    seenValues.put(normalizedValue, cellValue);
                }));
    }


    private static String normalizeSymmetricValue(String value) {
        String[] parts = value.split("-");
        Arrays.sort(parts);
        return String.join("-", parts);
    }

    /**
     * Method to display cell values as integer if numeric
     */
    static String getCellValue(Cell cell) {
        if (cell == null) {
            return "NULL";
        }

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double value = cell.getNumericCellValue();
                if (value == Math.floor(value)) {
                    yield String.valueOf((long) value);
                } else {
                    yield String.valueOf(value);
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "NULL";
        };
    }
}