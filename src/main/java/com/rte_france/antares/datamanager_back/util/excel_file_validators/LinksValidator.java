package com.rte_france.antares.datamanager_back.util.excel_file_validators;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.ExcelFileType;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.LinksColumns;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static com.rte_france.antares.datamanager_back.util.excel_file_validators.ExcelCommonValidator.*;

@Slf4j
@UtilityClass
public class LinksValidator {

    /**
     * @param path     trajectory to be added to database
     * @param fileType Links
     * @param horizon  sheet in file to be read
     */
    public static void linksDuplicateAndCellsValuesChecks(Path path, ExcelFileType fileType, String horizon) {
        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheet(horizon);
            if (fileType == ExcelFileType.LINKS) {
                checkForDuplicateValues(sheet, LinksColumns.NAME.getDisplayName(), path, horizon, true, TrajectoryType.LINK.name());
                checkColumnsRules(sheet, horizon, LinksColumns.getNumericColumnNames(), LinksColumns.getBooleanColumnNames(), Collections.singletonList(LinksColumns.NAME.getDisplayName()),TrajectoryType.LINK.name());
            }
        } catch (IOException e) {
            throw TechnicalException.builder()
                    .message("Could not check columns in file: {0}")
                    // .antaresErrorCode(antaresErrorCode.DASHBOARD_ERROR_001)
                    .errorMessageArguments(List.of(path.getFileName().toString()))
                    .cause(e.getCause())
                    .build();
        }
    }

    private static void checkColumnsRules(Sheet sheet, String horizon, List<String> numericColumns, List<String> booleanColumns, List<String> stringColumns, String trajectoryType) {
        checkNumbersAreIntegers(sheet, horizon, numericColumns);
        checkBooleanColumns(sheet, horizon, booleanColumns, trajectoryType);
        stringColumns.forEach(column -> ExcelCommonValidator.checkStringColumns(sheet, horizon, column, TrajectoryType.LINK.name()));
    }

    /**
     * @param sheet          to be read in Excel file
     * @param horizon        to make error clearer
     * @param numericColumns numeric columns must be integers and positive values
     */
    private static void checkNumbersAreIntegers(Sheet sheet, String horizon, List<String> numericColumns) {
        int nameColumnIndex = findColumnIndex(sheet, "Name", horizon, TrajectoryType.LINK.name());

        Map<String, Set<String>> notNumericByColumn = new HashMap<>();
        Map<String, Set<String>> notIntegerByColumn = new HashMap<>();
        Map<String, Set<String>> negativeValuesByColumn = new HashMap<>();

        for (String columnName : numericColumns) {
            int columnIndex = findColumnIndex(sheet, columnName, horizon, TrajectoryType.LINK.name());
            processColumn(sheet, nameColumnIndex, columnIndex, columnName, notNumericByColumn, notIntegerByColumn, negativeValuesByColumn);
        }

        handleErrors(notNumericByColumn, notIntegerByColumn, negativeValuesByColumn);
    }

    private static void processColumn(Sheet sheet, int nameColumnIndex, int columnIndex, String columnName,
                                      Map<String, Set<String>> notNumericByColumn,
                                      Map<String, Set<String>> notIntegerByColumn,
                                      Map<String, Set<String>> negativeValuesByColumn) {
        Set<String> notNumericLinks = new LinkedHashSet<>();
        Set<String> notIntegerLinks = new LinkedHashSet<>();
        Set<String> negativeLinks = new LinkedHashSet<>();

        for (int rowIndex = 1; rowIndex < sheet.getPhysicalNumberOfRows(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;

            validateCell(row, nameColumnIndex, columnIndex, notNumericLinks, notIntegerLinks, negativeLinks);
        }

        if (!notNumericLinks.isEmpty()) {
            notNumericByColumn.put(columnName, notNumericLinks);
        }
        if (!notIntegerLinks.isEmpty()) {
            notIntegerByColumn.put(columnName, notIntegerLinks);
        }
        if (!negativeLinks.isEmpty()) {
            negativeValuesByColumn.put(columnName, negativeLinks);
        }
    }

    private static void validateCell(Row row, int nameColumnIndex, int columnIndex,
                                     Set<String> notNumericLinks, Set<String> notIntegerLinks, Set<String> negativeLinks) {
        Cell nameCell = row.getCell(nameColumnIndex);
        Cell valueCell = row.getCell(columnIndex);

        if (nameCell != null) {
            String linkName = nameCell.getStringCellValue();
            if (valueCell == null || valueCell.getCellType() != CellType.NUMERIC) {
                notNumericLinks.add(linkName);
            } else {
                double value = valueCell.getNumericCellValue();
                if (value < 0) {
                    negativeLinks.add(linkName);
                } else if (value % 1 != 0) {
                    notIntegerLinks.add(linkName);
                }
            }
        }
    }

    private static void handleErrors(Map<String, Set<String>> notNumericByColumn,
                                     Map<String, Set<String>> notIntegerByColumn,
                                     Map<String, Set<String>> negativeValuesByColumn) {
        if (!notNumericByColumn.isEmpty()) {
            throwFormatError(notNumericByColumn);
        }
        if (!notIntegerByColumn.isEmpty()) {
            throwNotIntegerError(notIntegerByColumn);
        }
        if (!negativeValuesByColumn.isEmpty()) {
            throwNegativeError(negativeValuesByColumn);
        }
    }

        private static void throwFormatError(Map<String, Set<String>> notNumericByColumn) {
            String columnNames = notNumericByColumn.keySet().stream()
                    .sorted()
                    .collect(Collectors.joining(", "));

            String linkNames = notNumericByColumn.values().stream()
                    .flatMap(Set::stream)
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining(", "));

            throw BusinessException.builder()
                    .message("Waiting for Numeric Value(s) in column(s) {0} for link(s) in {1} LINK trajectory")
                    .errorMessageArguments(List.of(columnNames, linkNames))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        private static void throwNotIntegerError(Map<String, Set<String>> notIntegerByColumn) {
            String columnNames = notIntegerByColumn.keySet().stream()
                    .sorted()
                    .collect(Collectors.joining(", "));

            String linkNames = notIntegerByColumn.values().stream()
                    .flatMap(Set::stream)
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining(", "));

            throw BusinessException.builder()
                    .message("Waiting for Integer Value(s) (no decimal) in column(s) {0} for link(s) in {1} LINK trajectory")
                    .errorMessageArguments(List.of(columnNames, linkNames))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        private static void throwNegativeError(Map<String, Set<String>> negativeValuesByColumn) {
            String columnNames = negativeValuesByColumn.keySet().stream()
                    .sorted()
                    .collect(Collectors.joining(", "));

            String linkNames = negativeValuesByColumn.values().stream()
                    .flatMap(Set::stream)
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining(", "));

            throw BusinessException.builder()
                    .message("Waiting for Positive Value(s) in column(s) {0} for link(s) in {1} LINK trajectory")
                    .errorMessageArguments(List.of(columnNames, linkNames))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }









        /**
         * @param path    trajectory file
         * @param horizon make error clearer
         * @return parameters to be concatenated in warning links.all_values_zero created
         */

    public static List<String> checkPowerColumnsForZeroValues(Path path, String horizon) {
        List<String> numericColumns = LinksColumns.getNumericColumnNames();
        return findZeroValues(path, horizon, numericColumns);
    }

    private static boolean hasOnlyZeroValues(Row row, Sheet sheet, List<String> numericColumns, String horizon) {
        for (String column : numericColumns) {
            int index = findColumnIndex(sheet, column, horizon, TrajectoryType.LINK.name());
            Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell != null && cell.getCellType() == CellType.NUMERIC && cell.getNumericCellValue() != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param path    trajectory file
     * @param horizon make error clearer
     * @param columns grouped by Direct or Indirect types
     * @return parameters to be concatenated in warning links.unilateral_values_zero
     */

    public static List<String> areAllValuesZeroInGroup(Path path, String horizon, List<String> columns) {
        return findZeroValues(path, horizon, columns);
    }

    private static List<String> findZeroValues(Path path, String horizon, List<String> columns) {
        List<String> parametersForWarnings = new ArrayList<>();

        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheet(horizon);
            Set<String> warningLocations = new HashSet<>();


            for (int i = 1; i < sheet.getPhysicalNumberOfRows(); i++) {
                Row row = sheet.getRow(i);

                if (row != null && hasOnlyZeroValues(row, sheet, columns, horizon)) {

                    String location = String.format("%d,%d", i + 1, 0);
                    if (!warningLocations.contains(location)) {
                        parametersForWarnings.add(String.format("%d,%d,%s", i + 1, 1, path.getFileName()));
                        warningLocations.add(location);
                    }
                }
            }

        } catch (IOException e) {
            throw TechnicalException.builder()
                    .message("Could not check columns in file:  {0}")
                    .errorMessageArguments(List.of(path.getFileName().toString()))
                    .cause(e.getCause())
                    .build();
        }

        return parametersForWarnings;
    }

    /**
     * @param path                  trajectory file
     * @param horizon               make error clearer
     * @param columnName            grouped by Direct or Indirect types
     * @param areasSavedForScenario to verify alphabetical order of links
     * @return parameters to be concatenated in warning areas.not_alphabetically_ordered
     */
    public static List<String> checkLinksAlphabeticalOrder(Path path, String horizon, String columnName, List<String> areasSavedForScenario) {
        List<String> parametersForWarnings = new ArrayList<>();

        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheet(horizon);
            int columnIndex = findColumnIndex(sheet, columnName, horizon, TrajectoryType.LINK.name());

            sheet.forEach(row -> {
                Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (cell != null && cell.getCellType() == CellType.STRING) {
                    String value = cell.getStringCellValue().trim();
                    String[] parts = value.split("-");

                    if (parts.length == 2) {
                        String area1 = parts[0].trim();
                        String area2 = parts[1].trim();

                        if (area1.compareTo(area2) > 0 && areasSavedForScenario.contains(area1) && areasSavedForScenario.contains(area2)) {
                            parametersForWarnings.add(String.format("%s,%d,%d,%s", value, row.getRowNum() + 1, columnIndex + 1, path.getFileName()));
                        }
                    }
                }
            });
        } catch (IOException e) {
            throw TechnicalException.builder()
                    .message("Could not check links in file: {0}")
                    .errorMessageArguments(List.of(path.getFileName().toString()))
                    .cause(e.getCause())
                    .build();
        }

        return parametersForWarnings;
    }

}
