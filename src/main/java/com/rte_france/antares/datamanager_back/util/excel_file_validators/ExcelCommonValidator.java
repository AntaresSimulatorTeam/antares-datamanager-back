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


    public static final String AREAS = "areas";

    /**
     * @param path     trajectory file
     * @param fileType to verify columns names using ColumnEnums
     * @param horizon  sheet name to be read
     */
    public static void checkIfColumnsAreValid(Path path, ExcelFileType fileType, String horizon, String trajectoryType) {
        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = getSheet(workbook, path, horizon);
            Row headerRow = getHeaderRow(sheet, path);

            List<String> actualColumns = extractActualColumns(headerRow);
            validateColumns(fileType, actualColumns, horizon, trajectoryType);

            checkAllRowsHaveValues(sheet, fileType.getColumnCount(), horizon, trajectoryType);

        } catch (IOException e) {
            throw TechnicalException.builder()
                    .message("Error reading file {0}: " + path.getFileName())
                    .cause(e.getCause())
                    .build();
        }
    }

    private static Sheet getSheet(Workbook workbook, Path path, String horizon) {
        Sheet sheet = workbook.getSheet(horizon);
        if (sheet == null) {
            throw BusinessException.builder()
                    .message("File {0} does not contain the expected sheet: {1}")
                    .errorMessageArguments(List.of(path.getFileName().toString(), horizon))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        return sheet;
    }

    private static Row getHeaderRow(Sheet sheet, Path path) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw BusinessException.builder()
                    .message("File {0} does not contain a valid header row.")
                    .errorMessageArguments(List.of(path.getFileName().toString()))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        return headerRow;
    }

    private static List<String> extractActualColumns(Row headerRow) {
        List<String> actualColumns = new ArrayList<>();
        for (Cell cell : headerRow) {
            if (cell.getCellType() == CellType.STRING && !cell.getStringCellValue().trim().isEmpty()) {
                actualColumns.add(cell.getStringCellValue().trim());
            }
        }
        return actualColumns;
    }

    private static void validateColumns(ExcelFileType fileType, List<String> actualColumns, String horizon, String trajectoryType) {
        List<String> expectedColumns = fileType.getColumnNames();

        List<String> wrongColumnName = actualColumns.stream()
                .filter(actual -> actual != null && !actual.trim().isEmpty() &&
                        expectedColumns.stream()
                                .noneMatch(expected -> expected.equalsIgnoreCase(actual)))
                .toList();

        List<String> missingColumns = expectedColumns.stream()
                .filter(expected -> actualColumns.stream()
                        .noneMatch(actual -> actual != null &&
                                !actual.trim().isEmpty() &&
                                expected.equalsIgnoreCase(actual)))
                .toList();

        if (!wrongColumnName.isEmpty()) {
            throw BusinessException.builder()
                    .message("Invalid column(s) name(s): " + String.join(", ", wrongColumnName) +
                            " for horizon {0} in {1} trajectory")
                    .errorMessageArguments(List.of(horizon, trajectoryType))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        } else if (!missingColumns.isEmpty()) {
            throw BusinessException.builder()
                    .message("Columns: " + String.join(", ", missingColumns) +
                            " not found for horizon {0} in {1} trajectory")
                    .errorMessageArguments(List.of(horizon, trajectoryType))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }





    private static void checkAllRowsHaveValues(Sheet sheet, int columnCount, String horizon, String trajectoryType) {
        Set<String> areaValues = new HashSet<>();

        List<String> emptyCells = IntStream.rangeClosed(1, sheet.getLastRowNum())
                .mapToObj(sheet::getRow)
                .filter(row -> row != null && !isRowEmpty(row))
                .flatMap(row -> {
                    String prefix = "";
                    if (TrajectoryType.AREA.name().equals(trajectoryType)) {
                        int areasColumnIndex = findColumnIndex(sheet, AREAS, horizon, trajectoryType);
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


    public static void checkBooleanColumns(Sheet sheet, String horizon, List<String> booleanColumns, String trajectoryType) {
        Map<String, Integer> columnIndexes = booleanColumns.stream()
                .collect(Collectors.toMap(Function.identity(), column -> ExcelCommonValidator.findColumnIndex(sheet, column, horizon, trajectoryType)));

        String identifierColumn = (TrajectoryType.AREA.name().equals(trajectoryType))
                ? AreaColumns.AREAS.name()
                : ((TrajectoryType.LINK.name().equals(trajectoryType))
                ? LinksColumns.NAME.name()
                : null);


        int identifierColumnIndex = identifierColumn != null
                ? findColumnIndex(sheet, identifierColumn, horizon, trajectoryType)
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
                    .message("Waiting for boolean value(s) in column {0} in {1} trajectory")
                    .errorMessageArguments(List.of(
                            String.join(", ", booleanColumns),
                            trajectoryType))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }


    /**
     * @param sheet      to verify strings
     * @param horizon    sheet name
     * @param columnName where we expect values to be strings and throw error if a number is found
     */
    public static void checkStringColumns(Sheet sheet, String horizon, String columnName, String trajectoryType) {
        int columnIndex = findColumnIndex(sheet, columnName, horizon, trajectoryType);

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
                    return AREAS.equals(columnName) ?
                            actualValue :
                            "row " + entry.getKey() + ", Column " + (columnIndex + 1) + ": '" + actualValue + "'";
                })
                .toList();


        if (!invalidCells.isEmpty()) {
            throw BusinessException.builder()
                    .message(AREAS.equals(columnName) ?
                            "Waiting for String value for area(s): {2} in {3} trajectory" :
                            "Column {0} errors in sheet {1} in file:{2}. Locations: {3}")
                    .errorMessageArguments(List.of(columnName, horizon, String.join(", ", invalidCells),trajectoryType))
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
    @SuppressWarnings("unchecked")
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
     * @param horizon    to make error clearer
     * @return column index to be found
     */
    static int findColumnIndex(Sheet sheet, String columnName, String horizon, String trajectoryType) {
        Row headerRow = sheet.getRow(0);
        return IntStream.range(0, headerRow.getPhysicalNumberOfCells())
                .filter(i -> columnName.equalsIgnoreCase(headerRow.getCell(i).getStringCellValue()))
                .findFirst()
                .orElseThrow(() -> BusinessException.builder()
                        .message("Column {0} not found for horizon {1} in trajectory: {2}")
                        .errorMessageArguments(List.of(columnName, horizon, trajectoryType))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build());
    }

    public static void checkForDuplicateValues(Sheet sheet, String columnName, Path path, String horizon, boolean checkSymmetric, String trajectoryType) {
        int columnIndex = findColumnIndex(sheet, columnName, horizon, trajectoryType);
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