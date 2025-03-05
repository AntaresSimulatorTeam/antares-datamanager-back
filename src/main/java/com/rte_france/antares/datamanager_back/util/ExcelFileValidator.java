package com.rte_france.antares.datamanager_back.util;
import com.rte_france.antares.datamanager_back.exception.TechnicalAntaresDataMangerException;
import com.rte_france.antares.datamanager_back.util.columnsEnums.ExcelFileType;
import com.rte_france.antares.datamanager_back.util.columnsEnums.LinksColumns;
import lombok.Getter;
import org.apache.poi.ss.usermodel.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.IntStream;

@Getter
public class ExcelFileValidator {


    public static void checkIfColumnsAreValid(Path path, ExcelFileType fileType, String horizon) {
        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheet(horizon);

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new TechnicalAntaresDataMangerException("The file " + path.getFileName() + " does not contain any columns names.");
            }

            int columnCount = headerRow.getPhysicalNumberOfCells();
            if (columnCount != fileType.getColumnCount()) {
                throw new TechnicalAntaresDataMangerException("Invalid number of columns in sheet '" + horizon + "': Expected "
                        + fileType.getColumnCount() + ", but found " + columnCount);
            }

            List<String> actualColumns = new ArrayList<>();
            headerRow.forEach(cell -> actualColumns.add(cell.getStringCellValue()));

            if (!fileType.validateColumns(actualColumns)) {
                throw new TechnicalAntaresDataMangerException("Invalid column names in sheet '" + horizon + "' in file: " + path.getFileName());
            }

            checkAllRowsHaveValues(sheet, fileType.getColumnCount(), path, horizon);

            linksDuplicateAndCellsValuesChecks(path, fileType, horizon, sheet);

        } catch (IOException e) {
            throw new TechnicalAntaresDataMangerException("Could not check columns in file: " + e.getMessage());
        }
    }

    private static void linksDuplicateAndCellsValuesChecks(Path path, ExcelFileType fileType, String horizon, Sheet sheet) {
        if (fileType == ExcelFileType.LINKS) {
            checkForDuplicateValues(sheet, LinksColumns.NAME.getDisplayName(), path, horizon);
            checkNumericAndBooleanColumns(sheet, path, horizon, LinksColumns.getNumericColumnNames(), LinksColumns.getBooleanColumnNames());
        }
    }

    private static void checkAllRowsHaveValues(Sheet sheet, int columnCount, Path path, String horizon) {
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            for (int colIndex = 0; colIndex < columnCount; colIndex++) {
                Cell cell = row.getCell(colIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (cell == null || cell.getCellType() == CellType.BLANK ||
                        (cell.getCellType() == CellType.STRING && cell.getStringCellValue().trim().isEmpty())) {
                    throw new TechnicalAntaresDataMangerException("Empty value found in sheet '" + horizon + "' at row " + (rowIndex + 1) +
                            ", column " + (colIndex + 1) + " in file: " + path.getFileName());
                }
            }
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

    private static void checkNumericAndBooleanColumns(Sheet sheet, Path path, String horizon, List<String> numericColumns, List<String> booleanColumns) {

        numericColumns.forEach(column -> checkNumericColumn(sheet, path, horizon, column));

        booleanColumns.forEach(column -> checkBooleanColumn(sheet, path, horizon, column));
    }

    private static void checkNumericColumn(Sheet sheet, Path path, String horizon, String column) {
        int index = findColumnIndex(sheet, column, path, horizon);

        for (int i = 1; i < sheet.getPhysicalNumberOfRows(); i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);

                if (cell == null || cell.getCellType() != CellType.NUMERIC || cell.getNumericCellValue() <= 0 || cell.getNumericCellValue() % 1 != 0) {
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
            if (row != null) {
                Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);

                if (cell == null) {
                    throw new TechnicalAntaresDataMangerException("Missing value in column '" + column + "' in sheet '" + horizon +
                            "' in file: " + path.getFileName() + " - must be a boolean (true/false).");
                }

                if (cell.getCellType() == CellType.BOOLEAN) {
                    continue;
                }

                if (cell.getCellType() == CellType.STRING) {
                    String cellValue = cell.getStringCellValue().trim().toUpperCase();
                    if (!"TRUE".equals(cellValue) && !"FALSE".equals(cellValue)) {
                        throw new TechnicalAntaresDataMangerException("Invalid value in column '" + column + "' in sheet '" + horizon +
                                "' in file: " + path.getFileName() + " - must be a boolean (true/false).");
                    }
                    continue;
                }

                throw new TechnicalAntaresDataMangerException("Invalid value in column '" + column + "' in sheet '" + horizon +
                        "' in file: " + path.getFileName() + " - must be a boolean (true/false).");
            }
        }
    }

}