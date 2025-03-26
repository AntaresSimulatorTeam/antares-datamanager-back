package com.rte_france.antares.datamanager_back.util.excel_file_validators;

import com.rte_france.antares.datamanager_back.exception.TechnicalAntaresDataMangerException;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.AreaColumns;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.ExcelFileType;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.LinksColumns;
import lombok.experimental.UtilityClass;
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
                checkForDuplicateValues(sheet, LinksColumns.NAME.getDisplayName(), path, horizon, true);
                checkColumnsRules(sheet, path, horizon, LinksColumns.getNumericColumnNames(), LinksColumns.getBooleanColumnNames(), Collections.singletonList(LinksColumns.NAME.getDisplayName()));
            }
        } catch (IOException e) {
            throw new TechnicalAntaresDataMangerException("Could not check columns in file: " + e.getMessage());
        }
    }

    private static void checkColumnsRules(Sheet sheet, Path path, String horizon, List<String> numericColumns, List<String> booleanColumns, List<String> stringColumns) {
         checkNumbersAreIntegers(sheet, path, horizon, numericColumns);
         checkBooleanColumns(sheet, path, horizon, booleanColumns);
         stringColumns.forEach(column -> ExcelCommonValidator.checkStringColumns(sheet, path, horizon, column));
    }

    /**
     * @param sheet   to be read in Excel file
     * @param path    trajectory file
     * @param horizon to make error clearer
     * @param numericColumns  numeric columns must be integers and positive values
     */
    private static void checkNumbersAreIntegers(Sheet sheet, Path path, String horizon, List<String> numericColumns) {
        Map<String, Integer> columnIndexes = numericColumns.stream()
                .collect(Collectors.toMap(Function.identity(), column -> findColumnIndex(sheet, column, path, horizon)));

        List<String> invalidCells = IntStream.range(1, sheet.getPhysicalNumberOfRows())
                .mapToObj(sheet::getRow)
                .filter(Objects::nonNull)
                .filter(row -> row.getPhysicalNumberOfCells() > 0 &&
                        row.getCell(0) != null &&
                        !row.getCell(0).getStringCellValue().isEmpty())
                .flatMap(row -> columnIndexes.entrySet().stream()
                        .map(entry -> Map.entry(entry.getKey(), row.getCell(entry.getValue(), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)))
                        .filter(entry -> isInvalidNumber(entry.getValue()))
                        .map(entry -> String.format("Column '%s', Row %d, Value: %s",
                                entry.getKey(), row.getRowNum() + 1, getCellValue(entry.getValue()))))
                .toList();

        if (!invalidCells.isEmpty()) {
            throw new TechnicalAntaresDataMangerException(String.format(
                    "Invalid numeric values in sheet '%s' in file: %s. Details: %s",
                    horizon, path.getFileName(), String.join("; ", invalidCells)));
        }
    }


    private static boolean isInvalidNumber(Cell cell) {
        return cell == null || cell.getCellType() != CellType.NUMERIC || cell.getNumericCellValue() < 0 || cell.getNumericCellValue() % 1 != 0;
    }

    /**
     * Method to display cell values as integer if numeric
     */
    private static String getCellValue(Cell cell) {
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



    /**
     * @param path    trajectory file
     * @param horizon make error clearer
     * @return parameters to be concatenated in warning links.all_values_zero created
     */

    public static List<String> checkPowerColumnsForZeroValues(Path path, String horizon) {
        List<String> numericColumns = LinksColumns.getNumericColumnNames();
        return findZeroValues(path, horizon, numericColumns);
    }

    private static boolean hasOnlyZeroValues(Row row, Sheet sheet, List<String> numericColumns, Path path, String horizon) {
        for (String column : numericColumns) {
            int index = findColumnIndex(sheet, column, path, horizon);
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

                if (row != null && hasOnlyZeroValues(row, sheet, columns, path, horizon)) {

                    String location = String.format("%d,%d", i + 1, 0);
                    if (!warningLocations.contains(location)) {
                        parametersForWarnings.add(String.format("%d,%d,%s", i + 1, 1, path.getFileName()));
                        warningLocations.add(location);
                    }
                }
            }

        } catch (IOException e) {
            throw new TechnicalAntaresDataMangerException("Could not check columns in file: " + e.getMessage());
        }

        return parametersForWarnings;
    }

    /**
     * @param path trajectory file
     * @param horizon make error clearer
     * @param columnName grouped by Direct or Indirect types
     * @param areasSavedForScenario to verify alphabetical order of links
     * @return parameters to be concatenated in warning areas.not_alphabetically_ordered
     */
    public static List<String> checkLinksAlphabeticalOrder(Path path, String horizon, String columnName, List<String> areasSavedForScenario) {
        List<String> parametersForWarnings = new ArrayList<>();

        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheet(horizon);
            int columnIndex = findColumnIndex(sheet, columnName, path, horizon);

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
            throw new TechnicalAntaresDataMangerException("Could not check links in file: " + path.getFileName());
        }

        return parametersForWarnings;
    }

}
