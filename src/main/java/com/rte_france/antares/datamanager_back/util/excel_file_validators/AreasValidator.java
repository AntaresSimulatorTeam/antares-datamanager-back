package com.rte_france.antares.datamanager_back.util.excel_file_validators;

import com.rte_france.antares.datamanager_back.exception.TechnicalAntaresDataMangerException;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.AreaColumns;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.LinksColumns;
import org.apache.poi.ss.usermodel.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

import static com.rte_france.antares.datamanager_back.util.excel_file_validators.ExcelCommonValidator.*;


public class AreasValidator {

    private static final int AREAS_MAX_LENGTH = 10;
    public static void validateAreaColumns(Path path, String horizon) throws TechnicalAntaresDataMangerException {
        try (Workbook workbook = WorkbookFactory.create(Files.newInputStream(path))) {
            Sheet sheet = workbook.getSheet(horizon);
            if (sheet == null) {
                throw new TechnicalAntaresDataMangerException("Sheet '" + horizon + "' not found in file: " + path.getFileName());
            }

           checkColumnsRules(sheet, path, horizon, AreaColumns.getBooleanColumnNames(), AreaColumns.getStringColumnNames());
           checkAreasValuesLength(sheet, path, horizon, AreaColumns.AREAS.getDisplayName());
           checkForDuplicateValues(sheet, AreaColumns.AREAS.getDisplayName(), path, horizon, false);
        } catch (IOException e) {
            throw new TechnicalAntaresDataMangerException("Error reading file: " + path.getFileName());
        }
    }


    private static void checkColumnsRules(Sheet sheet, Path path, String horizon, List<String> booleanColumns, List<String> stringColumns) {
      checkBooleanColumns(sheet, path, horizon, booleanColumns);
      stringColumns.forEach(column -> ExcelCommonValidator.checkStringColumns(sheet, path, horizon, column));

    }


    private static void checkAreasValuesLength(Sheet sheet, Path path, String horizon, String columnName) {
        int columnIndex = findColumnIndex(sheet, columnName, path, horizon);

        List<String> invalidRows = IntStream.range(1, sheet.getPhysicalNumberOfRows())
                .mapToObj(sheet::getRow)
                .filter(row -> row != null && row.getPhysicalNumberOfCells() > 0
                        && row.getCell(0) != null && !row.getCell(0).getStringCellValue().isEmpty()) // Skip empty rows
                .filter(row -> {
                    Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (cell != null && cell.getCellType() == CellType.STRING) {
                        String value = cell.getStringCellValue().trim();
                        return value.length() > AreasValidator.AREAS_MAX_LENGTH;
                    }
                    return false;
                })
                .map(row -> "Row " + (row.getRowNum() + 1))
                .toList();

        if (!invalidRows.isEmpty()) {
            throw new TechnicalAntaresDataMangerException(String.format(
                    "Value too long for %s at row(s): %s in sheet '%s' in file: %s",
                    columnName, String.join(", ", invalidRows), horizon, path.getFileName())+ " maximum length is 10 characters");
        }
    }
}
