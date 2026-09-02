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
     * @param path     trajectory to be added to the database
     * @param fileType Links
     * @param horizon  sheet in file to be read
     */
    public static void linksDuplicateAndCellsValuesChecks(Path path, ExcelFileType fileType, String horizon) {
        linksDuplicateAndCellsValuesChecks(path, fileType, horizon, TrajectoryType.LINK);
    }

    /**
     * @param path     trajectory to be added to the database
     * @param fileType Links
     * @param horizon  sheet in file to be read
     * @param trajectoryType trajectory type (LINK or LINK_ME)
     */
    public static void linksDuplicateAndCellsValuesChecks(Path path, ExcelFileType fileType, String horizon, TrajectoryType trajectoryType) {
        String sheetName = horizon;
        
        if (TrajectoryType.LINK_ME == trajectoryType) {
            String[] parts = horizon.split("-");
            if (parts.length == 2) {
                sheetName = parts[1];
            }
        }
        
        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheet(sheetName);
            if (fileType == ExcelFileType.LINKS) {
                checkForDuplicateValues(sheet, LinksColumns.NAME.getDisplayName(), sheetName, true, trajectoryType.name());
                checkColumnsRules(sheet, sheetName, LinksColumns.getNumericColumnNames(), LinksColumns.getBooleanColumnNames(), Collections.singletonList(LinksColumns.NAME.getDisplayName()), trajectoryType.name());
                checkBooleanInParametersSheet(workbook, sheetName, 2, "HVDC");
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
        checkNumericColumns(sheet, horizon, numericColumns);
        checkBooleanColumns(sheet, horizon, booleanColumns, trajectoryType, true);
        stringColumns.forEach(column -> checkStringColumns(sheet, horizon, column, TrajectoryType.LINK.name()));
    }

    /**
     * @param sheet          to be read in the Excel file
     * @param horizon        to make error clearer
     * @param numericColumns numeric columns must be numeric and positive values
     */
    private static void checkNumericColumns(Sheet sheet, String horizon, List<String> numericColumns) {
        int nameColumnIndex = findColumnIndex(sheet, "Name", horizon, TrajectoryType.LINK.name());

        Map<String, Set<String>> notNumericByColumn = new HashMap<>();
        Map<String, Set<String>> negativeValuesByColumn = new HashMap<>();

        for (String columnName : numericColumns) {
            int columnIndex = findColumnIndex(sheet, columnName, horizon, TrajectoryType.LINK.name());
            boolean isOptional = columnName.toLowerCase(Locale.ROOT).startsWith("hvdc");
            processColumn(sheet, nameColumnIndex, columnIndex, columnName, notNumericByColumn, negativeValuesByColumn, isOptional);
        }

        handleErrors(notNumericByColumn, negativeValuesByColumn);
    }

/**
  * @param sheet The Excel sheet to process
  * @param nameColumnIndex Index of the column containing link names
  * @param columnIndex Index of the column to validate
  * @param columnName Name of the column to validate
  * @param notNumericByColumn Map to store links with non-numeric values by column
  * @param negativeValuesByColumn Map to store links with negative values by column
  **/

private static void processColumn(Sheet sheet, int nameColumnIndex, int columnIndex, String columnName,
                                   Map<String, Set<String>> notNumericByColumn,
                                   Map<String, Set<String>> negativeValuesByColumn,
                                   boolean isOptional) {
     Set<String> notNumericLinks = new LinkedHashSet<>();
     Set<String> negativeLinks = new LinkedHashSet<>();

     int lastRowNum = sheet.getLastRowNum();

     for (int rowIndex = 1; rowIndex <= lastRowNum; rowIndex++) {
         Row row = sheet.getRow(rowIndex);
         if (row == null) continue;

         Cell nameCell = row.getCell(nameColumnIndex);
         if (nameCell != null && nameCell.getCellType() == CellType.STRING) {
             String linkName = nameCell.getStringCellValue().trim();
             if (!linkName.isEmpty()) {
                 processValueCell(row.getCell(columnIndex), linkName, notNumericLinks, negativeLinks, isOptional);
             }
         }
     }

     addNonEmptyResults(columnName, notNumericLinks, negativeLinks,
             notNumericByColumn, negativeValuesByColumn);
}
    /**
     * Processes a value cell and checks its compliance with validation rules.
     * Handles empty cells, text cells, and numeric cells.
     * For text cells, attempts to parse numeric values considering both dots and commas as decimal separators.
     *
     * @param valueCell The cell to check
     * @param linkName The name of the link associated with the cell
     * @param notNumericLinks Set of links with non-numeric values
     * @param negativeLinks Set of links with negative values
     */

    private static void processValueCell(Cell valueCell, String linkName,
                                         Set<String> notNumericLinks, Set<String> negativeLinks,
                                         boolean isOptional) {
        if (valueCell == null || isCellEmpty(valueCell)) {
            if (!isOptional) {
                notNumericLinks.add(linkName);
            }
            return;
        }

        if (valueCell.getCellType() == CellType.STRING) {
            String stringValue = valueCell.getStringCellValue().trim();
            try {
                double value = Double.parseDouble(stringValue.replace(",", "."));
                checkNumericValue(value, linkName, negativeLinks);
            } catch (NumberFormatException e) {
                notNumericLinks.add(linkName);
            }
        } else if (valueCell.getCellType() == CellType.NUMERIC) {
            checkNumericValue(valueCell.getNumericCellValue(), linkName, negativeLinks);
        } else {
            notNumericLinks.add(linkName);
        }
    }
    /**
     * Adds non-empty results to the error collection maps.
     * Only updates maps if errors were found.
     *
     * @param columnName Name of the processed column
     * @param notNumericLinks Set of links with non-numeric values
     * @param negativeLinks Set of links with negative values
     * @param notNumericByColumn Destination map for non-numeric errors
     * @param negativeValuesByColumn Destination map for negative value errors
     */

    private static void addNonEmptyResults(String columnName,
                                           Set<String> notNumericLinks, Set<String> negativeLinks,
                                           Map<String, Set<String>> notNumericByColumn,
                                           Map<String, Set<String>> negativeValuesByColumn) {
        if (!notNumericLinks.isEmpty()) {
            notNumericByColumn.put(columnName, notNumericLinks);
        }
        if (!negativeLinks.isEmpty()) {
            negativeValuesByColumn.put(columnName, negativeLinks);
        }
    }
    /**
     * Checks if a numeric value complies with validation rules.
     * A value must be positive.
     *
     * @param value The numeric value to check
     * @param linkName The name of the link associated with the value
     * @param negativeLinks Set of links with negative values
     */

    private static void checkNumericValue(double value, String linkName, Set<String> negativeLinks) {
        if (value < 0) {
            negativeLinks.add(linkName);
        }
    }


    private static void handleErrors(Map<String, Set<String>> notNumericByColumn,
                                     Map<String, Set<String>> negativeValuesByColumn) {
        if (!notNumericByColumn.isEmpty()) {
            throwFormatError(notNumericByColumn);
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
                .message("Waiting for Numeric Value(s) in column(s) {0} for link(s) {1} in LINK trajectory")
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
                .message("Waiting for Positive Value(s) in column(s) {0} for link(s) {1} in LINK trajectory")
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
        List<String> isolatedLinks = new ArrayList<>();

        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheet(horizon);
            Set<String> processedLinks = new HashSet<>();
            int lastRowNum = sheet.getLastRowNum();

            for (int i = 1; i <= lastRowNum; i++) {
                Row row = sheet.getRow(i);
                if (row != null) {
                    Cell nameCell = row.getCell(0);
                    if (nameCell != null && nameCell.getCellType() == CellType.STRING) {
                        String linkName = nameCell.getStringCellValue().trim();
                        if (!linkName.isEmpty() && !processedLinks.contains(linkName) &&
                                hasOnlyZeroValues(row, sheet, columns, horizon)) {
                            isolatedLinks.add(linkName);
                            processedLinks.add(linkName);
                        }
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

        return isolatedLinks;
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
                            parametersForWarnings.add(value);
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

    private static void checkBooleanInParametersSheet(Workbook workbook, String horizon, int rowIndex, String parameterName) {
        Sheet parametersSheet = workbook.getSheet("parameters");

        Row headerRow = parametersSheet.getRow(0);
        if (headerRow == null) {
            return;
        }

        int horizonIndex = -1;
        for (Cell cell : headerRow) {
            if (cell.getCellType() == CellType.STRING && horizon.equals(cell.getStringCellValue().trim())) {
                horizonIndex = cell.getColumnIndex();
                break;
            }
        }

        if (horizonIndex != -1) {
            Row paramRow = parametersSheet.getRow(rowIndex);
            if (paramRow != null) {
                Cell paramCell = paramRow.getCell(horizonIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);

                if (!ExcelCommonValidator.isValidBoolean(paramCell)) {
                    throw BusinessException.builder()
                            .message("Waiting for boolean value(s) in column(s) {0} in {1} trajectory")
                            .errorMessageArguments(List.of(parameterName, TrajectoryType.LINK.name()))
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build();
                }
            }
        }
    }

}
