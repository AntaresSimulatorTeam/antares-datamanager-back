package com.rte_france.antares.datamanager_back.util.ExcelFileValidators;
import com.rte_france.antares.datamanager_back.exception.TechnicalAntaresDataMangerException;
import com.rte_france.antares.datamanager_back.util.ExcelFileValidators.ColumnsEnums.ExcelFileType;
import lombok.Getter;
import org.apache.poi.ss.usermodel.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Getter
public class ExcelCommonValidator {

    /**
     * @param path trajectory file
     * @param fileType to known columns names using ColumnEnums
     * @param horizon sheet name to be read
     */
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

            List<String> wrongColumnsName = fileType.checkColumnNames(actualColumns);
            if (!wrongColumnsName.isEmpty()) {
                throw new TechnicalAntaresDataMangerException("Invalid column names in sheet '" + horizon +
                        "' in file: " + path.getFileName() + ". Wrong column name for: " + String.join(", ", wrongColumnsName));
            }

            checkAllRowsHaveValues(sheet, fileType.getColumnCount(), path, horizon);


        } catch (IOException e) {
            throw new TechnicalAntaresDataMangerException("Could not check columns in file: " + e.getMessage());
        }
    }

    /**
     * @param sheet to be read
     * @param columnCount index of column to check if there is not any empty values
     * @param path trajectory file
     * @param horizon to make error clearer
     */
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

}