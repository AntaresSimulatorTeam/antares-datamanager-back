package com.rte_france.antares.datamanager_back.util.excel_file_validators;

import com.rte_france.antares.datamanager_back.exception.TechnicalAntaresDataMangerException;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.AreaColumns;
import org.apache.poi.ss.usermodel.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;


public class AreasValidator {

    public static void validateAreaColumns(Path path, String horizon) throws TechnicalAntaresDataMangerException {
        try (Workbook workbook = WorkbookFactory.create(Files.newInputStream(path))) {
            Sheet sheet = workbook.getSheet(horizon);
            if (sheet == null) {
                throw new TechnicalAntaresDataMangerException("Sheet '" + horizon + "' not found in file: " + path.getFileName());
            }

            checkColumnsRules(sheet, path, horizon, AreaColumns.getBooleanColumnNames(), AreaColumns.getStringColumnNames());
        } catch (IOException e) {
            throw new TechnicalAntaresDataMangerException("Error reading file: " + path.getFileName());
        }
    }

//    private static void validateAreasColumn(Sheet sheet, Path path, String horizon) {
//        Row headerRow = sheet.getRow(0);
//        if (headerRow == null) {
//            throw new TechnicalAntaresDataMangerException("Sheet '" + horizon + "' in file: " + path.getFileName() + " is empty.");
//        }
//
//        int areasColumnIndex = IntStream.range(0, headerRow.getPhysicalNumberOfCells())
//                .filter(colIndex -> {
//                    Cell cell = headerRow.getCell(colIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
//                    return cell != null && cell.getCellType() == CellType.STRING && "AREAS".equalsIgnoreCase(cell.getStringCellValue().trim());
//                })
//                .findFirst()
//                .orElseThrow(() -> new TechnicalAntaresDataMangerException("Column 'AREAS' not found in sheet '" + horizon + "' in file: " + path.getFileName()));
//
//        List<String> invalidCells = IntStream.rangeClosed(1, sheet.getLastRowNum())
//                .mapToObj(sheet::getRow)
//                .filter(Objects::nonNull)
//                .map(row -> Map.entry(row.getRowNum() + 1, row.getCell(areasColumnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)))
//                .filter(entry -> entry.getValue() != null && entry.getValue().getCellType() != CellType.STRING)
//                .map(entry -> "Row " + entry.getKey() + ", Column " + (areasColumnIndex + 1) +
//                        " (Expected STRING, found " + entry.getValue().getCellType() + ")")
//                .toList();
//
//        if (!invalidCells.isEmpty()) {
//            throw new TechnicalAntaresDataMangerException("AREAS column errors in sheet '" + horizon + "' in file: " + path.getFileName() +
//                    ". Locations: " + String.join(", ", invalidCells));
//        }
//    }

    private static void checkColumnsRules(Sheet sheet, Path path, String horizon, List<String> booleanColumns, List<String> stringColumns) {
        booleanColumns.forEach(column -> ExcelCommonValidator.checkBooleanColumn(sheet, path, horizon, column));
        stringColumns.forEach(column -> ExcelCommonValidator.checkStringColumns(sheet, path, horizon, column));

    }
}
