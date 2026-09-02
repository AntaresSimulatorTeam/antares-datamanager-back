package com.rte_france.antares.datamanager_back.util.excel_file_validators;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public class LinkMeValidator {

    public static final String COLUMN_A_NAME = "nodeFrom";
    public static final String COLUMN_B_NAME = "nodeTo";
    public static final String COLUMN_C_NAME = "Direct_MW";
    public static final String COLUMN_D_NAME = "Indirect_MW";
    public static final String COLUMN_E_NAME = "Hurdle Costs Direct";
    public static final String COLUMN_F_NAME = "Hurdle Costs Indirect";

    private static final int MAX_NAME_LENGTH = 60;
    private static final int MAX_TRAJECTORY_NAME_LENGTH = 40;

    /**
     * Validates the entire LINK_ME file
     *
     * @param path trajectory file path
     * @param sheetName sheet name (YYYY format)
     * @param trajectoryName trajectory name (directory name)
     */
    public static void validateLinkMeFile(Path path, String sheetName, String trajectoryName) {
        // Validate trajectory name length
        validateTrajectoryNameLength(trajectoryName);

        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw BusinessException.builder()
                        .message("Missing horizon {0} in LINKS_ME trajectory {1}")
                        .errorMessageArguments(List.of(sheetName, trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            // Validate each row
            for (Row row : sheet) {
                if (row.getRowNum() == 0) {
                    continue; // Skip header row
                }

                Cell cellA = row.getCell(0);
                Cell cellB = row.getCell(1);
                Cell cellC = row.getCell(2);
                Cell cellD = row.getCell(3);
                Cell cellE = row.getCell(4);
                Cell cellF = row.getCell(5);

                // Skip completely empty rows
                if (cellA == null && cellB == null && cellC == null && cellD == null && cellE == null && cellF == null) {
                    continue;
                }

                // Validate nodeFrom (Column A)
                validateNodeFromColumn(cellA, trajectoryName);

                // Validate nodeTo (Column B)
                validateNodeToColumn(cellB, trajectoryName);

                // Validate Direct_MW (Column C)
                validateDirectMwColumn(cellC, trajectoryName);

                // Validate Indirect_MW (Column D)
                validateIndirectMwColumn(cellD, trajectoryName);

                // Validate Hurdle Costs Direct (Column E)
                validateHurdleCostsDirectColumn(cellE, trajectoryName);

                // Validate Hurdle Costs Indirect (Column F)
                validateHurdleCostsIndirectColumn(cellF, trajectoryName);
            }

        } catch (IOException e) {
            throw TechnicalException.builder()
                    .message("Could not read LINKS_ME trajectory file: {0}")
                    .errorMessageArguments(List.of(path.getFileName().toString()))
                    .cause(e.getCause())
                    .build();
        }
    }

    /**
     * Validates trajectory name length (max 40 characters)
     */
    private static void validateTrajectoryNameLength(String trajectoryName) {
        if (trajectoryName.length() > MAX_TRAJECTORY_NAME_LENGTH) {
            throw BusinessException.builder()
                    .message("Trajectory name cannot exceed 40 characters")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    /**
     * Validates nodeFrom column (Column A)
     * - Must not be null if other columns have data
     * - Must not exceed 60 characters
     */
    private static void validateNodeFromColumn(Cell cell, String trajectoryName) {
        String value = getCellStringValue(cell);

        // Check if null when other columns might have data
        if (value == null || value.isEmpty()) {
            throw BusinessException.builder()
                    .message("{0} column must be filled in in LINKS_ME trajectory {1}")
                    .errorMessageArguments(List.of(COLUMN_A_NAME, trajectoryName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        // Check length
        if (value.length() > MAX_NAME_LENGTH) {
            throw BusinessException.builder()
                    .message("{0} cannot exceed 60 characters in LINKS_ME trajectory {1}")
                    .errorMessageArguments(List.of(COLUMN_A_NAME, trajectoryName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    /**
     * Validates nodeTo column (Column B)
     * - Must not be null if other columns have data
     * - Must not exceed 60 characters
     */
    private static void validateNodeToColumn(Cell cell, String trajectoryName) {
        String value = getCellStringValue(cell);

        // Check if null when other columns might have data
        if (value == null || value.isEmpty()) {
            throw BusinessException.builder()
                    .message("{0} column must be filled in in LINKS_ME trajectory {1}")
                    .errorMessageArguments(List.of(COLUMN_B_NAME, trajectoryName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        // Check length
        if (value.length() > MAX_NAME_LENGTH) {
            throw BusinessException.builder()
                    .message("{0} cannot exceed 60 characters in LINKS_ME trajectory {1}")
                    .errorMessageArguments(List.of(COLUMN_B_NAME, trajectoryName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    /**
     * Validates Direct_MW column (Column C)
     * - Must be numeric or 'infinite'
     */
    private static void validateDirectMwColumn(Cell cell, String trajectoryName) {
        if (cell == null || isCellEmpty(cell)) {
            return; // Optional column
        }

        String value = getCellStringValue(cell);
        if (value != null && !value.isEmpty()) {
            if (isNumericOrInfinite(value)) {
                throw BusinessException.builder()
                        .message("Column {0} must be numeric or 'infinite' in LINKS_ME trajectory {1}")
                        .errorMessageArguments(List.of(COLUMN_C_NAME, trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
        }
    }

    /**
     * Validates Indirect_MW column (Column D)
     * - Must be numeric or 'infinite'
     */
    private static void validateIndirectMwColumn(Cell cell, String trajectoryName) {
        if (cell == null || isCellEmpty(cell)) {
            return; // Optional column
        }

        String value = getCellStringValue(cell);
        if (value != null && !value.isEmpty()) {
            if (isNumericOrInfinite(value)) {
                throw BusinessException.builder()
                        .message("Column {0} must be numeric or 'infinite' in LINKS_ME trajectory {1}")
                        .errorMessageArguments(List.of(COLUMN_D_NAME, trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
        }
    }

    /**
     * Validates Hurdle Costs Direct column (Column E)
     * - Must be numeric
     */
    private static void validateHurdleCostsDirectColumn(Cell cell, String trajectoryName) {
        if (cell == null || isCellEmpty(cell)) {
            return; // Optional column
        }

        if (isNumericCell(cell)) {
            String value = getCellStringValue(cell);
            if (value != null && !value.isEmpty() && !isNumeric(value)) {
                throw BusinessException.builder()
                        .message("Column {0} must be numeric in LINKS_ME trajectory {1}")
                        .errorMessageArguments(List.of(COLUMN_E_NAME, trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
        }
    }

    /**
     * Validates Hurdle Costs Indirect column (Column F)
     * - Must be numeric
     */
    private static void validateHurdleCostsIndirectColumn(Cell cell, String trajectoryName) {
        if (cell == null || isCellEmpty(cell)) {
            return; // Optional column
        }

        if (isNumericCell(cell)) {
            String value = getCellStringValue(cell);
            if (value != null && !value.isEmpty() && !isNumeric(value)) {
                throw BusinessException.builder()
                        .message("Column {0} must be numeric in LINKS_ME trajectory {1}")
                        .errorMessageArguments(List.of(COLUMN_F_NAME, trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
        }
    }

    /**
     * Get cell value as string, handling different cell types
     */
    private static String getCellStringValue(Cell cell) {
        if (cell == null) {
            return null;
        }

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            case BLANK -> "";
            default -> null;
        };
    }

    /**
     * Check if cell is empty
     */
    private static boolean isCellEmpty(Cell cell) {
        if (cell == null) {
            return true;
        }
        return cell.getCellType() == CellType.BLANK || 
               (cell.getCellType() == CellType.STRING && cell.getStringCellValue().isEmpty());
    }

    /**
     * Check if cell is numeric
     */
    private static boolean isNumericCell(Cell cell) {
        if (cell == null) {
            return true;
        }
        return cell.getCellType() != CellType.NUMERIC;
    }

    /**
     * Check if value is numeric or 'infinite'
     */
    private static boolean isNumericOrInfinite(String value) {
        if (value == null || value.isEmpty()) {
            return false; // Empty is acceptable
        }
        if ("infinite".equalsIgnoreCase(value.trim())) {
            return false;
        }
        return isNumeric(value);
    }

    /**
     * Check if value is numeric
     */
    private static boolean isNumeric(String value) {
        if (value == null || value.isEmpty()) {
            return false; // Empty is acceptable
        }
        try {
            Double.parseDouble(value.replace(",", "."));
            return false;
        } catch (NumberFormatException e) {
            return true;
        }
    }
}
