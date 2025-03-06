package com.rte_france.antares.datamanager_back.util.ExcelFileValidators;

import com.rte_france.antares.datamanager_back.exception.TechnicalAntaresDataMangerException;
import com.rte_france.antares.datamanager_back.util.ExcelFileValidators.ColumnsEnums.ExcelFileType;
import com.rte_france.antares.datamanager_back.util.ExcelFileValidators.ColumnsEnums.LinksColumns;
import org.apache.poi.ss.usermodel.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.IntStream;


//public class LinksValidator {
//
//
//    public static void linksDuplicateAndCellsValuesChecks(Path path, ExcelFileType fileType, String horizon) {
//        try (InputStream inputStream = Files.newInputStream(path);
//             Workbook workbook = WorkbookFactory.create(inputStream)) {
//
//            Sheet sheet = workbook.getSheet(horizon);
//            if (fileType == ExcelFileType.LINKS) {
//
//                checkForDuplicateValues(sheet, LinksColumns.NAME.getDisplayName(), path, horizon);
//                checkNumericAndBooleanColumns(sheet, path, horizon, LinksColumns.getNumericColumnNames(), LinksColumns.getBooleanColumnNames());
//
//            }
//        } catch (IOException e) {
//            throw new TechnicalAntaresDataMangerException("Could not check columns in file: " + e.getMessage());
//        }
//    }
//
//    private static int findColumnIndex(Sheet sheet, String columnName, Path path, String horizon) {
//        Row headerRow = sheet.getRow(0);
//
//        return IntStream.range(0, headerRow.getPhysicalNumberOfCells())
//                .filter(i -> columnName.equalsIgnoreCase(headerRow.getCell(i).getStringCellValue()))
//                .findFirst()
//                .orElseThrow(() -> new TechnicalAntaresDataMangerException(
//                        "Column '" + columnName + "' not found in sheet '" + horizon + "' in file: " + path.getFileName()));
//    }
//
//    private static void checkForDuplicateValues(Sheet sheet, String columnName, Path path, String horizon) {
//        int columnIndex = findColumnIndex(sheet, columnName, path, horizon);
//        Set<String> seenValues = new HashSet<>();
//
//        sheet.forEach(row -> Optional.ofNullable(row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
//                .map(Cell::getStringCellValue)
//                .map(String::trim)
//                .filter(cellValue -> !cellValue.isEmpty())
//                .ifPresent(cellValue -> {
//                    if (!seenValues.add(cellValue)) {
//                        throw new TechnicalAntaresDataMangerException("Duplicate value '" + cellValue + "' found in column '" + columnName +
//                                "' in sheet '" + horizon + "' in file: " + path.getFileName());
//                    }
//                }));
//    }
//
//    private static void checkNumericAndBooleanColumns(Sheet sheet, Path path, String horizon, List<String> numericColumns, List<String> booleanColumns) {
//
//        numericColumns.forEach(column -> checkNumericColumn(sheet, path, horizon, column));
//
//        booleanColumns.forEach(column -> checkBooleanColumn(sheet, path, horizon, column));
//    }
//
//    private static void checkNumericColumn(Sheet sheet, Path path, String horizon, String column) {
//        int index = findColumnIndex(sheet, column, path, horizon);
//
//        for (int i = 1; i < sheet.getPhysicalNumberOfRows(); i++) {
//            Row row = sheet.getRow(i);
//            if (row != null) {
//                Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
//
//                if (cell == null || cell.getCellType() != CellType.NUMERIC || cell.getNumericCellValue() <= 0 || cell.getNumericCellValue() % 1 != 0) {
//                    throw new TechnicalAntaresDataMangerException("Invalid value in column '" + column + "' in sheet '" + horizon +
//                            "' in file: " + path.getFileName() + " - must be a positive integer.");
//                }
//            }
//        }
//    }
//
//    private static void checkBooleanColumn(Sheet sheet, Path path, String horizon, String column) {
//        int index = findColumnIndex(sheet, column, path, horizon);
//
//        for (int i = 1; i < sheet.getPhysicalNumberOfRows(); i++) {
//            Row row = sheet.getRow(i);
//            if (row != null) {
//                Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
//
//                if (cell == null) {
//                    throw new TechnicalAntaresDataMangerException("Missing value in column '" + column + "' in sheet '" + horizon +
//                            "' in file: " + path.getFileName() + " - must be a boolean (true/false).");
//                }
//
//                if (cell.getCellType() == CellType.BOOLEAN) {
//                    continue;
//                }
//
//                if (cell.getCellType() == CellType.STRING) {
//                    String cellValue = cell.getStringCellValue().trim().toUpperCase();
//                    if (!"TRUE".equals(cellValue) && !"FALSE".equals(cellValue)) {
//                        throw new TechnicalAntaresDataMangerException("Invalid value in column '" + column + "' in sheet '" + horizon +
//                                "' in file: " + path.getFileName() + " - must be a boolean (true/false).");
//                    }
//                    continue;
//                }
//
//                throw new TechnicalAntaresDataMangerException("Invalid value in column '" + column + "' in sheet '" + horizon +
//                        "' in file: " + path.getFileName() + " - must be a boolean (true/false).");
//            }
//        }
//    }
//
//    public static boolean checkPowerColumnsForZeroValues(Path path, String horizon, List<String> numericColumns) {
//        try (InputStream inputStream = Files.newInputStream(path);
//             Workbook workbook = WorkbookFactory.create(inputStream)) {
//
//            Sheet sheet = workbook.getSheet(horizon);
//
//            for (int i = 1; i < sheet.getPhysicalNumberOfRows(); i++) {
//                Row row = sheet.getRow(i);
//                if (row != null) {
//                    boolean rowHasNonZeroValue = false;
//
//                    for (String column : numericColumns) {
//                        int index = findColumnIndex(sheet, column, path, horizon);
//                        Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
//
//                        if (cell != null && cell.getCellType() == CellType.NUMERIC && cell.getNumericCellValue() > 0) {
//                            rowHasNonZeroValue = true;
//                            break;
//                        }
//                    }
//
//                    // If the row has no non-zero values, return false
//                    if (!rowHasNonZeroValue) {
//                        return false;
//                    }
//                }
//            }
//
//            return true;
//
//        } catch (IOException e) {
//            e.printStackTrace(); // Handle exception as needed
//            return false;
//        }
//    }
//}
public class LinksValidator {

    public static void linksDuplicateAndCellsValuesChecks(Path path, ExcelFileType fileType, String horizon) {
        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheet(horizon);
            if (fileType == ExcelFileType.LINKS) {
                checkForDuplicateValues(sheet, LinksColumns.NAME.getDisplayName(), path, horizon);
                checkColumns(sheet, path, horizon, LinksColumns.getNumericColumnNames(), LinksColumns.getBooleanColumnNames());
            }
        } catch (IOException e) {
            throw new TechnicalAntaresDataMangerException("Could not check columns in file: " + e.getMessage());
        }
    }

    private static int findColumnIndex(Sheet sheet, String columnName, Path path, String horizon) {
        Row headerRow = sheet.getRow(0);
        return IntStream.range(0, headerRow.getPhysicalNumberOfCells())
                .filter(i -> columnName.equalsIgnoreCase(headerRow.getCell(i).getStringCellValue()))
                .findFirst()
                .orElseThrow(() -> new TechnicalAntaresDataMangerException(
                        "Column '" + columnName + "' not found in sheet '" + horizon + "' in file: " + path.getFileName()));
    }

    private static void checkForDuplicateValues(Sheet sheet, String columnName, Path path, String horizon) {
        int columnIndex = findColumnIndex(sheet, columnName, path, horizon);
        Set<String> seenValues = new HashSet<>();

        sheet.forEach(row -> Optional.ofNullable(row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                .map(Cell::getStringCellValue)
                .map(String::trim)
                .filter(cellValue -> !cellValue.isEmpty())
                .ifPresent(cellValue -> {
                    if (!seenValues.add(cellValue)) {
                        throw new TechnicalAntaresDataMangerException("Duplicate value '" + cellValue + "' found in column '" + columnName +
                                "' in sheet '" + horizon + "' in file: " + path.getFileName());
                    }
                }));
    }

    private static void checkColumns(Sheet sheet, Path path, String horizon, List<String> numericColumns, List<String> booleanColumns) {
        // Reuse the same method for numeric and boolean checks
        numericColumns.forEach(column -> checkNumericColumn(sheet, path, horizon, column));
        booleanColumns.forEach(column -> checkBooleanColumn(sheet, path, horizon, column));
    }

    private static void checkNumericColumn(Sheet sheet, Path path, String horizon, String column) {
        int index = findColumnIndex(sheet, column, path, horizon);

        for (int i = 1; i < sheet.getPhysicalNumberOfRows(); i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);

                if (cell == null || cell.getCellType() != CellType.NUMERIC || cell.getNumericCellValue() <0 || cell.getNumericCellValue() % 1 != 0) {
                    throw new TechnicalAntaresDataMangerException("Invalid value in column '" + column + "' in sheet '" + horizon +
                            "' in file: " + path.getFileName() + " - must be a positive integer.");
                }
            }
        }
    }

    private static void checkBooleanColumn(Sheet sheet, Path path, String horizon, String column) {
        int index = findColumnIndex(sheet, column, path, horizon);

        for (int i = 1; i < sheet.getPhysicalNumberOfRows(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell == null || !isValidBoolean(cell)) {
                throw new TechnicalAntaresDataMangerException(String.format(
                        "Invalid value in column '%s' in sheet '%s' in file: %s - must be a boolean (true/false).",
                        column, horizon, path.getFileName()));
            }
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

    public static boolean checkPowerColumnsForZeroValues(Path path, String horizon) {
        List<String> numericColumns = LinksColumns.getNumericColumnNames();
        return findZeroValues(path, horizon, numericColumns);
    }

    private static boolean hasNonZeroValueInColumns(Row row, Sheet sheet, List<String> numericColumns, Path path, String horizon) {
        for (String column : numericColumns) {
            int index = findColumnIndex(sheet, column, path, horizon);
            Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell != null && cell.getCellType() == CellType.NUMERIC && cell.getNumericCellValue() > 0) {
                return true;
            }
        }
        return false;
    }



    public static boolean areAllValuesZeroInGroup(Path path, String horizon, List<String> columns)  {
        return findZeroValues(path, horizon, columns);
    }

    private static boolean findZeroValues(Path path, String horizon, List<String> columns) {
        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheet(horizon);

            for (int i = 1; i < sheet.getPhysicalNumberOfRows(); i++) {
                Row row = sheet.getRow(i);
                if (row != null && !hasNonZeroValueInColumns(row, sheet, columns, path, horizon)) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new TechnicalAntaresDataMangerException("Could not check columns in file: " + e.getMessage());
        }
    }


}
