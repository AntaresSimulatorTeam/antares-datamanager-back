package com.rte_france.antares.datamanager_back.util;
import com.rte_france.antares.datamanager_back.exception.TechnicalAntaresDataMangerException;
import com.rte_france.antares.datamanager_back.util.columnsEnums.ExcelFileType;
import lombok.Getter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

        // ✅ Check column count
        int columnCount = headerRow.getPhysicalNumberOfCells();
        if (columnCount != fileType.getColumnCount()) {
            throw new TechnicalAntaresDataMangerException("Invalid number of columns in sheet '" + horizon + "': Expected "
                    + fileType.getColumnCount() + ", but found " + columnCount);
        }

        // ✅ Extract and validate column names
        List<String> actualColumns = new ArrayList<>();
        headerRow.forEach(cell -> actualColumns.add(cell.getStringCellValue()));

        if (fileType.validateColumns(actualColumns)) {
            throw new TechnicalAntaresDataMangerException("Invalid column names in sheet '" + horizon + "' in file: " + path.getFileName());
        }

    } catch (IOException e) {
        throw new TechnicalAntaresDataMangerException("Could not check columns in file: " + e.getMessage());
    }
}
}