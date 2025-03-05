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
import java.util.stream.Collectors;
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

            Optional.ofNullable(fileType == ExcelFileType.LINKS ? "Names" : null)
                    .ifPresent(columnName -> checkForDuplicateValues(sheet, columnName, path, horizon));

            checkNumericColumns(sheet,path,horizon);

        } catch (IOException e) {
            throw new TechnicalAntaresDataMangerException("Could not check columns in file: " + e.getMessage());
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

    private static void checkNumericColumns(Sheet sheet, Path path, String horizon) {
        List<String> numericColumns = LinksColumns.getNumericColumnNames();

        numericColumns.stream()
                .map(column -> Map.entry(column, findColumnIndex(sheet, column, path, horizon)))
                .forEach(entry -> {
                    String column = entry.getKey();
                    int index = entry.getValue();

                    sheet.forEach(row -> {
                        Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);

                        if (cell == null || cell.getCellType() != CellType.NUMERIC || cell.getNumericCellValue() <= 0 || cell.getNumericCellValue() % 1 != 0) {
                            throw new TechnicalAntaresDataMangerException("Invalid value in column '" + column + "' in sheet '" + horizon +
                                    "' in file: " + path.getFileName() + " - must be a positive integer.");
                        }
                    });
                });
    }
}