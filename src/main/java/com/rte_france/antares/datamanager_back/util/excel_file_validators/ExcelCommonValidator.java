package com.rte_france.antares.datamanager_back.util.excel_file_validators;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.AreaColumns;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.ExcelFileType;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.LinksColumns;
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
    public static void checkIfColumnsAreValid(Path path, ExcelFileType fileType, String horizon, String trajectoryType) {
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


            if (!missingColumns.isEmpty() || !invalidColumns.isEmpty()) {
                StringBuilder errorMessage = new StringBuilder();

                if (!missingColumns.isEmpty()) {
                    errorMessage.append("Columns: ")
                            .append(String.join(", ", missingColumns))
                            .append(" not found");
                }

                if (!invalidColumns.isEmpty()) {
                    if (!missingColumns.isEmpty()) {
                        errorMessage.append(". ");
                    }
                    errorMessage.append("Invalid columns names: ")
                            .append(String.join(", ", invalidColumns));
                }

                errorMessage.append(" for horizon '{0}' in {1} trajectory");

                throw BusinessException.builder()
                        .message(errorMessage.toString())
                        .errorMessageArguments(List.of(horizon, trajectoryType))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }



            checkAllRowsHaveValues(sheet, fileType.getColumnCount(), path, horizon, trajectoryType);

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
    private static void checkAllRowsHaveValues2(Sheet sheet, int columnCount, Path path, String horizon, String trajectoryType) {
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
                .map(entry -> "Row " + entry.getKey() + " - Column " + entry.getValue())
                .toList();

        if (!emptyCells.isEmpty()) {
            throw BusinessException.builder()
                    .message("Empty values found for {0}(s) for horizon {1} in {2} trajectory")
                    // .antaresErrorCode(antaresErrorCode.DASHBOARD_ERROR_001)
                    .errorMessageArguments(List.of(trajectoryType.toLowerCase(), horizon, trajectoryType))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private static void checkAllRowsHaveValues(Sheet sheet, int columnCount, Path path, String horizon, String trajectoryType) {
        Set<String> areaValues = new HashSet<>();

        List<String> emptyCells = IntStream.rangeClosed(1, sheet.getLastRowNum())
                .mapToObj(sheet::getRow)
                .filter(row -> row != null && !isRowEmpty(row))
                .flatMap(row -> {
                    String prefix = "";
                    if (TrajectoryType.AREA.name().equals(trajectoryType)) {
                        int areasColumnIndex = findColumnIndex(sheet, "areas", path, horizon, trajectoryType);
                        String areaValue = Optional.ofNullable(row.getCell(areasColumnIndex))
                                .map(ExcelCommonValidator::getCellValue)
                                .orElse("");
                        areaValues.add(areaValue);
                    }

                    String finalPrefix = prefix;
                    return IntStream.range(0, columnCount)
                            .mapToObj(colIndex -> {
                                Cell cell = row.getCell(colIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                                if (cell == null || isCellEmpty(cell)) {
                                    return finalPrefix + "Row " + (row.getRowNum() + 1) + " - Column " + (colIndex + 1);
                                }
                                return null;
                            })
                            .filter(Objects::nonNull);
                })
                .toList();

        if (!emptyCells.isEmpty()) {
            String areasMessage = TrajectoryType.AREA.name().equals(trajectoryType)
                    ? String.join(", ", areaValues)
                    : "";

            throw BusinessException.builder()
                    .message("Empty values found for {0}(s): {1} for horizon {2} in {3} trajectory")
                    .errorMessageArguments(List.of(
                            trajectoryType.toLowerCase(),
                            areasMessage,
                            horizon,
                            trajectoryType))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }


    private static boolean isCellEmpty(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim().isEmpty();
            case NUMERIC, BOOLEAN, FORMULA -> false;
            case BLANK -> true;
            default -> true;
        };
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
    public static void checkBooleanColumns2(Sheet sheet, Path path, String horizon, List<String> booleanColumns, String trajectoryType) {
        Map<String, Integer> columnIndexes = booleanColumns.stream()
                .collect(Collectors.toMap(Function.identity(), column -> ExcelCommonValidator.findColumnIndex(sheet, column, path, horizon, trajectoryType)));

        List<String> invalidCells = IntStream.rangeClosed(1, sheet.getLastRowNum())
                .mapToObj(sheet::getRow)
                .filter(Objects::nonNull)
                .flatMap(row -> columnIndexes.entrySet().stream()
                        .map(entry -> Map.entry(entry.getKey(),
                                Map.entry(row.getRowNum() + 1, row.getCell(entry.getValue(), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK))))
                        .filter(entry -> !isValidBoolean(entry.getValue().getValue()))
                        .map(entry -> String.format("Row %d - Column %d",
                                entry.getValue().getKey(),
                                entry.getValue().getValue().getColumnIndex() + 1)))
                .toList();

        if (!invalidCells.isEmpty()) {
            throw BusinessException.builder()
                    .message("Waiting for boolean value(s) in column {0} for {1}(s): in {2} trajectory")
                    // .antaresErrorCode(antaresErrorCode.DASHBOARD_ERROR_001)
                    .errorMessageArguments(List.of(String.join(", ", invalidCells), path.getFileName().toString(), trajectoryType))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }
    public static void checkBooleanColumns(Sheet sheet, Path path, String horizon, List<String> booleanColumns, String trajectoryType) {
        Map<String, Integer> columnIndexes = booleanColumns.stream()
                .collect(Collectors.toMap(Function.identity(), column -> ExcelCommonValidator.findColumnIndex(sheet, column, path, horizon, trajectoryType)));

        String identifierColumn = (TrajectoryType.AREA.name().equals(trajectoryType))
                ? AreaColumns.AREAS.name()
                : ((TrajectoryType.LINK.name().equals(trajectoryType))
                ? LinksColumns.NAME.name()
                : null);


        int identifierColumnIndex = identifierColumn != null
                ? findColumnIndex(sheet, identifierColumn, path, horizon, trajectoryType)
                : -1;

        List<String> invalidIdentifiers = IntStream.rangeClosed(1, sheet.getLastRowNum())
                .mapToObj(sheet::getRow)
                .filter(Objects::nonNull)
                .filter(row -> columnIndexes.entrySet().stream()
                        .anyMatch(entry -> {
                            Cell cell = row.getCell(entry.getValue(), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                            return !isValidBoolean(cell);
                        }))
                .map(row -> Optional.of(row)
                        .filter(r -> identifierColumnIndex >= 0)
                        .map(r -> r.getCell(identifierColumnIndex))
                        .map(ExcelCommonValidator::getCellValue)
                        .orElse(""))
                .distinct()
                .toList();

        if (!invalidIdentifiers.isEmpty()) {
            throw BusinessException.builder()
                    .message("Waiting for boolean value(s) in column {0} for {1}(s): in {2} trajectory")
                    .errorMessageArguments(List.of(
                            String.join(", ", booleanColumns),
                            String.join(", ", invalidIdentifiers),
                            trajectoryType))
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
    public static void checkStringColumns(Sheet sheet, Path path, String horizon, String columnName, String trajectoryType) {
        int columnIndex = findColumnIndex(sheet, columnName, path, horizon, trajectoryType);

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
                    return "areas".equals(columnName) ?
                            actualValue :
                            "row " + entry.getKey() + ", Column " + (columnIndex + 1) + ": '" + actualValue + "'";
                })
                .toList();

        if (!invalidCells.isEmpty()) {
            throw BusinessException.builder()
                    .message("areas".equals(columnName) ?
                            "Waiting for String value for area(s): {3} in {4} trajectory" :
                            "Column {0} errors in sheet {1} in file:{2}. Locations: {3}")
                    .errorMessageArguments(List.of(columnName, horizon, path.getFileName().toString(), String.join(", ", invalidCells),trajectoryType))
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
    static int findColumnIndex(Sheet sheet, String columnName, Path path, String horizon, String trajectoryType) {
        Row headerRow = sheet.getRow(0);
        return IntStream.range(0, headerRow.getPhysicalNumberOfCells())
                .filter(i -> columnName.equalsIgnoreCase(headerRow.getCell(i).getStringCellValue()))
                .findFirst()
                .orElseThrow(() -> BusinessException.builder()
                        .message("Column {0} not found for horizon {1} in  trajectory: {2}")
                        .errorMessageArguments(List.of(columnName, horizon, trajectoryType))
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
//    public static void checkForDuplicateValues(Sheet sheet, String columnName, Path path, String horizon, boolean checkSymmetric, String trajectoryType) {
//        int columnIndex = findColumnIndex(sheet, columnName, path, horizon, trajectoryType);
//        Map<String, String> seenValues = new HashMap<>();
//
//        sheet.forEach(row -> Optional.ofNullable(row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
//                .map(ExcelCommonValidator::getCellValue)
//                .map(String::trim)
//                .filter(cellValue -> !cellValue.isEmpty())
//                .ifPresent(cellValue -> {
//                    String normalizedValue = checkSymmetric ? normalizeSymmetricValue(cellValue) : cellValue;
//
//                    if (seenValues.containsKey(normalizedValue)) {
//                        String firstOccurrence = seenValues.get(normalizedValue);
//
//                        // Avoid showing the same value twice if they are identical
//                        if (checkSymmetric && firstOccurrence.equals(cellValue)) {
//                            throw BusinessException.builder()
//                                    .message("Duplicate value for {0}(s): {1} for {2} trajectory")
//                                    .errorMessageArguments(List.of(trajectoryType.toLowerCase(), cellValue, trajectoryType))
//                                    .httpStatus(HttpStatus.BAD_REQUEST)
//                                    .build();
//                        } else {
//                            throw BusinessException.builder()
//                                    .message("Duplicate value for {0}(s): {1} for horizon {2}")
//                                    .errorMessageArguments(List.of(trajectoryType.toLowerCase(), cellValue, horizon))
//                                    .httpStatus(HttpStatus.BAD_REQUEST)
//                                    .build();
//                        }
//                    }
//
//                    seenValues.put(normalizedValue, cellValue);
//                }));
//    }
    public static void checkForDuplicateValues(Sheet sheet, String columnName, Path path, String horizon, boolean checkSymmetric, String trajectoryType) {
        int columnIndex = findColumnIndex(sheet, columnName, path, horizon, trajectoryType);
        Map<String, String> seenValues = new HashMap<>();
        Set<String> identicalDuplicates = new TreeSet<>();
        Set<String> symmetricDuplicates = new TreeSet<>();

        sheet.forEach(row -> Optional.ofNullable(row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                .map(ExcelCommonValidator::getCellValue)
                .map(String::trim)
                .filter(cellValue -> !cellValue.isEmpty())
                .ifPresent(cellValue -> {
                    String normalizedValue = checkSymmetric ? normalizeSymmetricValue(cellValue) : cellValue;

                    if (seenValues.containsKey(normalizedValue)) {
                        String firstOccurrence = seenValues.get(normalizedValue);
                        if (firstOccurrence.equals(cellValue)) {
                            identicalDuplicates.add(cellValue);
                        } else if (checkSymmetric) {
                            symmetricDuplicates.add(firstOccurrence);
                            symmetricDuplicates.add(cellValue);
                        }
                    }
                    seenValues.put(normalizedValue, cellValue);
                }));

        if (!symmetricDuplicates.isEmpty()) {
            String duplicatesList = String.join(", ", symmetricDuplicates);
            throw BusinessException.builder()
                    .message("Duplicate value in column 'Name' for horizon {0} in {1} trajectory. Values: {2} are considered identical")
                    .errorMessageArguments(List.of(horizon, trajectoryType, duplicatesList))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        if (!identicalDuplicates.isEmpty()) {
            String duplicatesList = String.join(", ", identicalDuplicates);
            throw BusinessException.builder()
                    .message("Duplicate value for {0}(s): {1} for {2} trajectory")
                    .errorMessageArguments(List.of(trajectoryType.toLowerCase(), duplicatesList, trajectoryType))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
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