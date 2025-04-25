package com.rte_france.antares.datamanager_back.util.ExcelFilesValidators;

import com.rte_france.antares.datamanager_back.exception.TechnicalAntaresDataMangerException;
import com.rte_france.antares.datamanager_back.util.CreateExcelTestUtil;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.ExcelCommonValidator;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.ExcelFileType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AreaFileValidatorTest {
    @TempDir
    Path tempDir;

    private Path tempFile;


    @Test
    void checkColumnsOKWhenColumnsAndDataAreValid() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFile( tempDir,"ValidFile.xlsx", "2035-2036",
                List.of("areas", "Power To Gas", "Stockage court terme", "x", "y", "r", "g", "b"),
                List.of(
                        List.of("Area1", "True", "True", "230", "420", "128", "260", "113"),
                        List.of("Area2", "False", "True", "168", "650", "125", "265", "113")
                )
        );

        ExcelCommonValidator.checkIfColumnsAreValid(tempFile, ExcelFileType.AREAS, "2035-2036");
    }

    @Test
    void shouldFailWhenColumnNamesAreInvalid() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFile( tempDir,"InvalidColumns.xlsx", "2036-2037",
                List.of("areastt", "Gas Power", "Storage", "x", "y", "r", "g", "b"),
                List.of(
                        List.of("Area1", "false", "true", "131", "425", "125", "230", "125")
                )
        );

        assertThrows(TechnicalAntaresDataMangerException.class, () ->
                ExcelCommonValidator.checkIfColumnsAreValid(tempFile, ExcelFileType.AREAS, "2036-2037"));
    }

    @Test
    void shouldFailWhenEmptyCellsArePresent() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFile(tempDir,"EmptyCells.xlsx","2037-2038",
                List.of("areas", "Power to Gas", "Stockage court terme", "x", "y", "r", "g", "b"),
                List.of(
                        List.of("A1", "10", "", "3", "4", "1", "2", "3")
                )
        );

        assertThrows(TechnicalAntaresDataMangerException.class, () ->
                ExcelCommonValidator.checkIfColumnsAreValid(tempFile, ExcelFileType.AREAS, "2037-2038"));
    }

    @Test
    void checkStringColumnsShouldThrowExceptionWhenDataIsInvalid() throws IOException {

        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "ErrorFile.xlsx", "2035-2036",
                List.of("areas", "Power To Gas", "Stockage court terme", "x", "y", "r", "g", "b"),
                List.of(
                        List.of(123, "True", "True", "230", "420", "128", "260", "113"),
                        List.of("Area2", "False", "True", "168", "650", "125", "265", "113")
                )
        );

        try (Workbook workbook = WorkbookFactory.create(Files.newInputStream(tempFile))) {
            Sheet sheet = workbook.getSheet("2035-2036");

            try {
                ExcelCommonValidator.checkStringColumns(sheet, tempFile, "2037-2038", "areas");
                fail("Expected TechnicalAntaresDataMangerException to be thrown");
            } catch (TechnicalAntaresDataMangerException e) {
                assertTrue(e.getMessage().contains("Invalid value: '123'"));
            }
        }
    }



}
