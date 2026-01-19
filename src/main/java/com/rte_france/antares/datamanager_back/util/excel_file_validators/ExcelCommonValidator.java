package com.rte_france.antares.datamanager_back.util.excel_file_validators;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.AreaColumns;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.ExcelFileType;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.LinksColumns;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@UtilityClass
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
        Set<String> emptyRowIdentifiers = new TreeSet<>();

        List<String> emptyCells = IntStream.rangeClosed(1, sheet.getLastRowNum())
                .mapToObj(sheet::getRow)
                .filter(row -> row != null && !isRowEmpty(row))
                .flatMap(row -> {
                    String identifier = "";
                    if (TrajectoryType.AREA.name().equals(trajectoryType)) {
                        int areasColumnIndex = findColumnIndex(sheet, AREAS, horizon, trajectoryType);
                        identifier = Optional.ofNullable(row.getCell(areasColumnIndex))
                                .map(ExcelCommonValidator::getCellValue)
                                .orElse("");
                    } else if (TrajectoryType.LINK.name().equals(trajectoryType)) {
                        int nameColumnIndex = findColumnIndex(sheet, "Name", horizon, trajectoryType);
                        identifier = Optional.ofNullable(row.getCell(nameColumnIndex))
                                .map(ExcelCommonValidator::getCellValue)
                                .orElse("");
                    }

                    final String finalIdentifier = identifier;
                    return IntStream.range(0, columnCount)
                            .mapToObj(colIndex -> {
                                Cell cell = row.getCell(colIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                                if (cell == null || isCellEmpty(cell)) {
                                    emptyRowIdentifiers.add(finalIdentifier);
                                    return "Row " + (row.getRowNum() + 1) + " - Column " + (colIndex + 1);
                                }
                                return null;
                            })
                            .filter(Objects::nonNull);
                })
                .toList();

        if (!emptyCells.isEmpty()) {
            String identifiersMessage = String.join(", ", emptyRowIdentifiers);

            throw BusinessException.builder()
                    .message("Empty values found for {0}(s): {1} for horizon {2} in {3} trajectory")
                    .errorMessageArguments(List.of(
                            trajectoryType.toLowerCase(),
                            identifiersMessage,
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
    public static boolean isRowEmpty(Row row) {
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
                    .message("Waiting for boolean value(s) in column(s) {0} in {1} trajectory")
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
                    if (isInvalidOrUndefinedCell(cell)) return false;

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
        if (isInvalidOrUndefinedCell(cell)) return true;
        var value = getBooleanCellValue(cell);
        return value.isPresent();
    }

    public static boolean isInvalidOrUndefinedCell(Cell cell) {
        return cell == null || cell.getCellType() == CellType.BLANK;
    }

    /**
     * @param cell to check
     * @return boolean value expected and avoid null for formatted cells
     */
    public static Optional<Boolean> getBooleanCellValue(Cell cell) {
        // If the cell is null or blank, we return empty to indicate an invalid or undefined value
        if (isInvalidOrUndefinedCell(cell)) return Optional.empty();

        // If the cell contains a boolean value, return it directly
        if (cell.getCellType() == CellType.BOOLEAN) return Optional.of(cell.getBooleanCellValue());

        // If the cell contains a string representation of true/false, parse it
        if (cell.getCellType() == CellType.STRING) {
            String value = cell.getStringCellValue().trim().toUpperCase();
            if ("TRUE".equals(value)) return Optional.of(true);
            if ("FALSE".equals(value)) return Optional.of(false);
        }
        return Optional.empty();
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

    public static void checkForDuplicateValues(Sheet sheet, String columnName, String horizon, boolean checkSymmetric, String trajectoryType) {
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

    public static void checkNumericDataCMorMR(Path file, String trajectoryName, String fileType) {
        String trajectoryLabel = switch (fileType.toUpperCase()) {
            case "CM" -> "THERMAL Cost Modulation trajectory ";
            case "MR" -> "THERMAL Must Run trajectory ";
            default -> throw BusinessException.builder()
                    .message("Unknown file type: " + fileType)
                    .build();
        };

        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw BusinessException.builder()
                        .message("Missing header row in " + trajectoryLabel + trajectoryName)
                        .build();
            }

            //CSV with comma delimiter
            String delimiter = ",";
            String[] headers = headerLine.split(delimiter, -1);
            int lastColumn = headers.length;

            String line;
            int rowIndex = 1;

            while ((line = reader.readLine()) != null && rowIndex <= 8760) {
                String[] values = line.split(delimiter, -1);

                for (int colIndex = 2; colIndex < lastColumn && colIndex < values.length; colIndex++) {
                    String valueStr = values[colIndex].trim();
                    if (valueStr.isEmpty()) continue; // blanks allowed

                    try {
                        //replace comma with dot for decimal parsing
                        Double.parseDouble(valueStr.replace(',', '.'));
                    } catch (NumberFormatException e) {
                        String clusterName = headers[colIndex].trim();
                        throw BusinessException.builder()
                                .message("Values for cluster " + clusterName
                                        + " are not numeric in " + trajectoryLabel + trajectoryName)
                                .build();
                    }
                }

                rowIndex++;
            }

        } catch (IOException e) {
            throw BusinessException.builder()
                    .message("Unable to read file for " + trajectoryLabel + trajectoryName)
                    .build();
        }
    }



}