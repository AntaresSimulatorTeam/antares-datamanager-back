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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static com.rte_france.antares.datamanager_back.util.excel_file_validators.ExcelCommonValidator.*;


public class AreasValidator {

    private static final int AREAS_MAX_LENGTH = 10;
    private static final int DISTRICT_MAX_LENGTH = 20;

    private AreasValidator() {

    }

    public static void validateAreaColumns(Path path, String horizon) throws BusinessException {
        try (Workbook workbook = WorkbookFactory.create(Files.newInputStream(path))) {
            Sheet sheet = workbook.getSheet(horizon);
            if (sheet == null) {
                throw BusinessException.builder()
                        .message("Sheet {0} not found in file: {1}")
                        .errorMessageArguments(List.of(horizon, path.getFileName().toString()))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            checkColumnsRules(sheet, horizon, new ArrayList<>(), AreaColumns.getStringColumnNames(), AreaColumns.getNumericalColumnNames(), TrajectoryType.AREA.name());
            checkColumnValueLength(sheet, horizon, AreaColumns.AREAS.getDisplayName(), AREAS_MAX_LENGTH);
            checkColumnValueLength(sheet, horizon, AreaColumns.DISTRICT.getDisplayName(), DISTRICT_MAX_LENGTH);
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

    /**
     * Validates that the length of values in a specified column within a sheet does not exceed the maximum allowed length.
     * If any value in the specified column exceeds the maximum length, a BusinessException is thrown.
     *
     * @param sheet      the sheet to validate
     * @param horizon    the specified horizon parameter for error context
     * @param columnName the name of the column containing values to be validated
     * @param maxLength  the maximum allowed length for the values in the column
     */
    public static void checkColumnValueLength(Sheet sheet, String horizon, String columnName, int maxLength) {
        int columnIndex = findColumnIndex(sheet, columnName, horizon, TrajectoryType.AREA.name());

        List<String> invalidRows = IntStream.range(1, sheet.getPhysicalNumberOfRows())
                .mapToObj(sheet::getRow)
                .filter(row -> row != null && row.getPhysicalNumberOfCells() > 0
                        && row.getCell(0) != null && !row.getCell(0).getStringCellValue().isEmpty()) // Skip empty rows
                .filter(row -> {
                    Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (cell != null && cell.getCellType() == CellType.STRING) {
                        String value = cell.getStringCellValue().trim();
                        return value.length() > maxLength;
                    }
                    return false;
                })
                .map(row -> row.getCell(columnIndex).getStringCellValue().trim())
                .toList();


        if (!invalidRows.isEmpty()) {
            var invalidRowsJoin = String.join(", ", invalidRows);
            throw BusinessException.builder()
                    .message("Value too long for {0}(s) : {1} in {2} trajectory")
                    .errorMessageArguments(List.of(columnName, invalidRowsJoin, TrajectoryType.AREA.name()))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }
}
