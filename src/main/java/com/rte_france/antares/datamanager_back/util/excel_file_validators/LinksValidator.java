package com.rte_france.antares.datamanager_back.util.excel_file_validators;

import com.rte_france.antares.datamanager_back.exception.TechnicalAntaresDataMangerException;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.ExcelFileType;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.LinksColumns;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
                ExcelCommonValidator.checkForDuplicateValues(sheet, LinksColumns.NAME.getDisplayName(), path, horizon);
                checkColumnsRules(sheet, path, horizon, LinksColumns.getNumericColumnNames(), LinksColumns.getBooleanColumnNames());
            }
        } catch (IOException e) {
            throw new TechnicalAntaresDataMangerException("Could not check columns in file: " + e.getMessage());
        }
    }

    private static void checkColumnsRules(Sheet sheet, Path path, String horizon, List<String> numericColumns, List<String> booleanColumns) {
        numericColumns.forEach(column -> checkNumbersAreIntegers(sheet, path, horizon, column));
        booleanColumns.forEach(column -> ExcelCommonValidator.checkBooleanColumn(sheet, path, horizon, column));
    }

    /**
     * @param sheet   to be read in Excel file
     * @param path    trajectory file
     * @param horizon to make error clearer
     * @param column  numeric columns must be integers and positive values
     */
    private static void checkNumbersAreIntegers(Sheet sheet, Path path, String horizon, String column) {
        int index = ExcelCommonValidator.findColumnIndex(sheet, column, path, horizon);

        for (int i = 1; i < sheet.getPhysicalNumberOfRows(); i++) {
            Row row = sheet.getRow(i);
            if (row != null && row.getPhysicalNumberOfCells() != 0 && row.getCell(0) != null && !row.getCell(0).getStringCellValue().isEmpty()) {
                Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);

                if (cell == null || cell.getCellType() != CellType.NUMERIC || cell.getNumericCellValue() < 0 || cell.getNumericCellValue() % 1 != 0) {
                    String value = (cell == null) ? "NULL" : String.valueOf(cell);

                    throw new TechnicalAntaresDataMangerException("Invalid value in column '" + column + "' in sheet '" + horizon +
                            "' in file: " + path.getFileName() + ". Wrong value: " + value + " (row: " + (i + 1) + ")");
                }
            }
        }
    }


    /**
     * @param path    trajectory file
     * @param horizon make error clearer
     * @return if all LinksColumns.getNumericColumnNames() are 0 a true is returned
     * in order to have a warning links.all_values_zero created
     */
    public static boolean checkPowerColumnsForZeroValues(Path path, String horizon) {
        List<String> numericColumns = LinksColumns.getNumericColumnNames();
        return findZeroValues(path, horizon, numericColumns);
    }

    private static boolean hasOnlyZeroValues(Row row, Sheet sheet, List<String> numericColumns, Path path, String horizon) {
        for (String column : numericColumns) {
            int index = ExcelCommonValidator.findColumnIndex(sheet, column, path, horizon);
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
     * @return true if all values are 0 in order to create a warning
     * links.direct_values_zero or links.indirect_values_zero
     */

    public static boolean areAllValuesZeroInGroup(Path path, String horizon, List<String> columns) {
        return findZeroValues(path, horizon, columns);
    }

    private static boolean findZeroValues(Path path, String horizon, List<String> columns) {
        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheet(horizon);

            for (int i = 1; i < sheet.getPhysicalNumberOfRows(); i++) {
                Row row = sheet.getRow(i);
                if (row != null && hasOnlyZeroValues(row, sheet, columns, path, horizon)) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new TechnicalAntaresDataMangerException("Could not check columns in file: " + e.getMessage());
        }
    }


}
