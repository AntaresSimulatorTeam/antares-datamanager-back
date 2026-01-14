package com.rte_france.antares.datamanager_back.util.excel_file_validators;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.AreaColumns;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

import static com.rte_france.antares.datamanager_back.util.excel_file_validators.ExcelCommonValidator.*;


public class AreasValidator {

    private static final int AREAS_MAX_LENGTH = 10;

    private AreasValidator() {

    }

    public static void validateAreaColumns(Path path, String horizon) throws BusinessException {
        try (Workbook workbook = WorkbookFactory.create(Files.newInputStream(path))) {
            Sheet sheet = workbook.getSheet(horizon);
            if (sheet == null) {
                throw BusinessException.builder()
                        .message("Sheet {0} not found in file: {1}")
                        // .antaresErrorCode(antaresErrorCode.DASHBOARD_ERROR_001)
                        .errorMessageArguments(List.of(horizon, path.getFileName().toString()))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            checkColumnsRules(sheet, horizon, AreaColumns.getBooleanColumnNames(), AreaColumns.getStringColumnNames(), AreaColumns.getNumericalColumnNames(), TrajectoryType.AREA.name());
            checkAreasValuesLength(sheet, horizon, AreaColumns.AREAS.getDisplayName());
            checkForDuplicateValues(sheet, AreaColumns.AREAS.getDisplayName(), horizon, false, TrajectoryType.AREA.name());
        } catch (IOException e) {
            throw TechnicalException.builder()
                    .message("Error reading file:  {0}")
                    // .antaresErrorCode(antaresErrorCode.DASHBOARD_ERROR_001)
                    .errorMessageArguments(List.of(path.getFileName().toString()))
                    .cause(e.getCause())
                    .build();
        }
    }

    private static void checkColumnsRules(Sheet sheet, String horizon, List<String> booleanColumns, List<String> stringColumns, List<String> numericalColumns, String trajectoryType) {
        checkBooleanColumns(sheet, horizon, booleanColumns, trajectoryType);
        checkNumericalColumns(sheet, horizon, numericalColumns, trajectoryType);
        stringColumns.forEach(column -> ExcelCommonValidator.checkStringColumns(sheet, horizon, column, TrajectoryType.AREA.name()));
    }
    
    public static void checkAreasValuesLength(Sheet sheet, String horizon, String columnName) {
        int columnIndex = findColumnIndex(sheet, columnName, horizon, TrajectoryType.AREA.name());

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
                .map(row -> row.getCell(columnIndex).getStringCellValue().trim())
                .toList();


        if (!invalidRows.isEmpty()) {
            var invalidRowsJoin = String.join(", ", invalidRows);
            throw BusinessException.builder()
                    .message("Value too long for area(s) : {0} in {1} trajectory")
                    .errorMessageArguments(List.of(invalidRowsJoin, TrajectoryType.AREA.name()))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }
}
