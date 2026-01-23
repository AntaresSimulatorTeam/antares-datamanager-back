package com.rte_france.antares.datamanager_back.util.ExcelFilesValidators;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.util.CreateExcelTestUtil;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.AreasValidator;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.ExcelCommonValidator;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.ExcelFileType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
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
                List.of("areas", "district", "spilled energy cost", "unsupplied energy cost", "x", "y", "r", "g", "b"),
                List.of(
                        List.of("Area1", "Dist1", 100.0, 200.0, "230", "420", "128", "260", "113"),
                        List.of("Area2", "Dist2", "150.0", "250.0", "168", "650", "125", "265", "113")
                )
        );

        ExcelCommonValidator.checkIfColumnsAreValid(tempFile, ExcelFileType.AREAS, "2035-2036", TrajectoryType.AREA.name());
    }

    @Test
    void shouldFailWhenColumnNamesAreInvalid() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "InvalidColumns.xlsx", "2036-2037",
                List.of("areastt", "spilled energy cost", "unsupplied e", "x", "y", "r", "g", "b"),
                List.of(
                        List.of("Area1", "false", "true", "131", "425", "125", "230", "125")
                )
        );

        BusinessException exception = assertThrows(BusinessException.class, () ->
                ExcelCommonValidator.checkIfColumnsAreValid(tempFile, ExcelFileType.AREAS, "2036-2037", TrajectoryType.AREA.name()));

        assertAll(
                () -> assertEquals(
                        "Invalid column(s) name(s): areastt, unsupplied e for horizon {0} in {1} trajectory",
                        exception.getMessage()),
                () -> assertEquals(
                        List.of("2036-2037", TrajectoryType.AREA.name()),
                        exception.getErrorMessageArguments()),
                () -> assertEquals(
                        HttpStatus.BAD_REQUEST,
                        exception.getHttpStatus())

        );
    }

    @Test
    void shouldFailWhenColumnAreMissing() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "InvalidColumns.xlsx", "2036-2037",
                List.of("", "", "unsupplied energy cost", "x", "y", "r", "g", "b"),
                List.of(
                        List.of("Area1", "false", "true", "131", "425", "125", "230", "125")
                )
        );

        BusinessException exception = assertThrows(BusinessException.class, () ->
                ExcelCommonValidator.checkIfColumnsAreValid(tempFile, ExcelFileType.AREAS, "2036-2037", TrajectoryType.AREA.name()));

        assertAll(
                () -> assertEquals(
                        "Columns: areas, district, spilled energy cost not found for horizon {0} in {1} trajectory",
                        exception.getMessage()),
                () -> assertEquals(
                        List.of("2036-2037", TrajectoryType.AREA.name()),
                        exception.getErrorMessageArguments()),
                () -> assertEquals(
                        HttpStatus.BAD_REQUEST,
                        exception.getHttpStatus())
        );
    }

    @Test
    void shouldFailWhenEmptyCellsArePresent() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "EmptyCells.xlsx", "2037-2038",
                List.of("areas", "district","spilled energy cost", "unsupplied energy cost", "x", "y", "r", "g", "b"),
                List.of(
                        List.of("B1", "BB1", "", "", "4", "1", "2", "3",""),
                        List.of("A2", "AA2", "", "", "4", "1", "2", "3","")
                )
        );

        BusinessException exception = assertThrows(BusinessException.class, () ->
                ExcelCommonValidator.checkIfColumnsAreValid(tempFile, ExcelFileType.AREAS, "2037-2038", TrajectoryType.AREA.name()));


        assertAll(
                () -> assertEquals("Empty values found for {0}(s): {1} for horizon {2} in {3} trajectory",
                        exception.getMessage()),
                () -> assertIterableEquals(
                        List.of(TrajectoryType.AREA.name().toLowerCase(),"A2, B1", "2037-2038", "AREA"),
                        exception.getErrorMessageArguments()),
                () -> assertEquals(HttpStatus.BAD_REQUEST,
                        exception.getHttpStatus())
        );

    }

    @Test
    void checkIfColumnsAreValid_shouldThrowTechnicalException_whenFileCannotBeRead() {
        Path invalidFile = tempDir.resolve("test_not_exist.xlsx");

        TechnicalException exception = assertThrows(TechnicalException.class, () ->
                ExcelCommonValidator.checkIfColumnsAreValid(invalidFile, ExcelFileType.AREAS, "2040-2041", TrajectoryType.AREA.name()));

        assertTrue(exception.getMessage().startsWith("Error reading file"));
    }


    @Test
    void checkStringAreaColumnShouldThrowExceptionWhenDataIsInvalid() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "ErrorFile.xlsx", "2035-2036",
                List.of("areas", "district", "spilled energy cost ", "unsupplied energy cost", "x", "y", "r", "g", "b"),
                List.of(
                        List.of(123, "district", 2.3, 3.2, "230", "420", "128", "260", "113"),
                        List.of(456, "distirct", 5.2, 8.5, "650", "125", "265", "113")
                )
        );

        try (Workbook workbook = WorkbookFactory.create(Files.newInputStream(tempFile))) {
            Sheet sheet = workbook.getSheet("2035-2036");

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> ExcelCommonValidator.checkStringColumns(sheet, "2035-2036", "areas", TrajectoryType.AREA.name()));

            assertAll(
                    () -> assertEquals("Waiting for String value for area(s): {2} in {3} trajectory",
                            exception.getMessage()),
                    () -> assertIterableEquals(
                            List.of("areas", "2035-2036", "123, 456", TrajectoryType.AREA.name()),
                            exception.getErrorMessageArguments()),
                    () -> assertEquals(HttpStatus.BAD_REQUEST,
                            exception.getHttpStatus())
            );
        }
    }

    @Test
    void checkStringDistrictColumnShouldThrowExceptionWhenDataIsInvalid() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "ErrorFile.xlsx", "2035-2036",
                List.of("areas", "district", "spilled energy cost ", "unsupplied energy cost", "x", "y", "r", "g", "b"),
                List.of(
                        List.of("area1", 400, 2.3, 3.2, "230", "420", "128", "260", "113"),
                        List.of("area2", 200, 5.2, 8.5, "650", "125", "265", "113")
                )
        );

        try (Workbook workbook = WorkbookFactory.create(Files.newInputStream(tempFile))) {
            Sheet sheet = workbook.getSheet("2035-2036");

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> ExcelCommonValidator.checkStringColumns(sheet, "2035-2036", "district", TrajectoryType.AREA.name()));

            assertAll(
                    () -> assertEquals("Column {0} errors in sheet {1} in file:{2}. Locations: {3}",
                            exception.getMessage()),
                    () -> assertIterableEquals(
                            List.of("district", "2035-2036", "row 2, Column 2: '400', row 3, Column 2: '200'", TrajectoryType.AREA.name()),
                            exception.getErrorMessageArguments()),
                    () -> assertEquals(HttpStatus.BAD_REQUEST,
                            exception.getHttpStatus())
            );
        }
    }



    @Test
    void checkAreasValuesLengthShouldThrowExceptionWhenValueIsTooLong() throws IOException {

        String longAreaName = "aBcDeFgHiJkLmNoPqR";
        String longAreaName2 = "aBcDeFgHiJkLmNoPqR56";


        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "TooLongArea.xlsx", "2035-2036",
                List.of("areas", "district", "Power To Gas", "Stockage court terme", "x", "y", "r", "g", "b"),
                List.of(
                        List.of(longAreaName, "Dist1", "True", "True", "230", "420", "128", "260", "113"),
                        List.of(longAreaName2, "Dist2", "False", "True", "168", "650", "125", "265", "113")
                )
        );

        try (Workbook workbook = WorkbookFactory.create(Files.newInputStream(tempFile))) {
            Sheet sheet = workbook.getSheet("2035-2036");

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> AreasValidator.checkColumnValueLength(sheet, "2035-2036", "areas", 10));

            assertAll(
                    () -> assertEquals("Value too long for {0}(s) : {1} in {2} trajectory",
                            exception.getMessage()),
                    () -> assertIterableEquals(
                            List.of(
                                    "areas",
                                    String.format("%s, %s", longAreaName, longAreaName2),
                                    TrajectoryType.AREA.name()
                            ),
                            exception.getErrorMessageArguments()),
                    () -> assertEquals(HttpStatus.BAD_REQUEST,
                            exception.getHttpStatus())
            );
        }
    }



    @Test
    void checkDistrictValuesLengthShouldThrowExceptionWhenValueIsTooLong() throws IOException {

        String longDistrictName = "aBcDeFgHiJkLmNoPqRsTuVwXyZ";
        String longDistrictName2 = "aBcDeFgHiJkLmNoPqRsTuVwXyZ123456";


        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "TooLongDistrict.xlsx", "2035-2036",
                List.of("areas", "district", "Power To Gas", "Stockage court terme", "x", "y", "r", "g", "b"),
                List.of(
                        List.of("Area1", longDistrictName, "True", "True", "230", "420", "128", "260", "113"),
                        List.of("Area2", longDistrictName2, "False", "True", "168", "650", "125", "265", "113")
                )
        );

        try (Workbook workbook = WorkbookFactory.create(Files.newInputStream(tempFile))) {
            Sheet sheet = workbook.getSheet("2035-2036");

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> AreasValidator.checkColumnValueLength(sheet, "2035-2036", "district", 20));

            assertAll(
                    () -> assertEquals("Value too long for {0}(s) : {1} in {2} trajectory",
                            exception.getMessage()),
                    () -> assertIterableEquals(
                            List.of(
                                    "district",
                                    String.format("%s, %s", longDistrictName, longDistrictName2),
                                    TrajectoryType.AREA.name()
                            ),
                            exception.getErrorMessageArguments()),
                    () -> assertEquals(HttpStatus.BAD_REQUEST,
                            exception.getHttpStatus())
            );
        }
    }

    @Test
    void checkIfColumnsAreValid_shouldThrowBusinessException_whenSheetIsMissing() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "test_sheet.xlsx", "missing",
                List.of("areas", "district","spilled energy cost", "unsupplied energy cost", "x", "y", "r", "g", "b"),
                List.of(
                        List.of("Area1", "True", "True", "230", "420", "128", "260", "113")
                )
        );

        BusinessException exception = assertThrows(BusinessException.class, () ->
                ExcelCommonValidator.checkIfColumnsAreValid(tempFile, ExcelFileType.AREAS, "2030-2040", TrajectoryType.AREA.name()));

        assertAll(
                () -> assertEquals(
                        "File {0} does not contain the expected sheet: {1}",
                        exception.getMessage()),
                () -> assertEquals(
                        List.of("test_sheet.xlsx", "2030-2040"),
                        exception.getErrorMessageArguments()),
                () -> assertEquals(
                        HttpStatus.BAD_REQUEST,
                        exception.getHttpStatus())
        );
    }

    @Test
    void checkColumnsDuplicated() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFile( tempDir,"ValidFile.xlsx", "2035-2036",
                List.of("areas", "district", "spilled energy cost", "unsupplied energy cost", "x", "y", "r", "g", "b"),
                List.of(
                        List.of("Area1", "district1", 10.2, 3.2, "230", "420", "128", "260", "113"),
                        List.of("Area1", "district2", 200.3, 20, "168", "650", "125", "265", "113"),
                        List.of("Area2", "district1", 50, 15, "168", "650", "125", "265", "113"),
                        List.of("Area2", "district2", 35, 70.3, "650", "125", "265", "113")
                )
        );
        try (Workbook workbook = WorkbookFactory.create(Files.newInputStream(tempFile))) {
            Sheet sheet = workbook.getSheet("2035-2036");

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> ExcelCommonValidator.checkForDuplicateValues(sheet, "areas","2035-2036", false, TrajectoryType.AREA.name()));

            assertAll(
                    () -> assertEquals("Duplicate value for {0}(s): {1} for {2} trajectory",
                            exception.getMessage()),
                    () -> assertIterableEquals(
                            List.of("area", "Area1, Area2", TrajectoryType.AREA.name()),
                            exception.getErrorMessageArguments()),
                    () -> assertEquals(HttpStatus.BAD_REQUEST,
                            exception.getHttpStatus())
            );
        }


    }

    @Test
    void shouldNotThrowWhenAllNumeric() throws Exception {
        tempFile = CreateExcelTestUtil.createExcelFile(
                tempDir,
                "ValidNumericColumns.xlsx",
                "2036-2037",
                List.of("areas", "district", "spilled energy cost", "unsupplied energy cost", "x", "y", "r", "g", "b"),
                List.of(
                        List.of("Area1", "district1", 2.0, 50.2, 131, 425, 125, 230, 125),
                        List.of("Area2", "district2", 4.5, 50.2,200, 300, 150, 210, 180)
                )
        );

        Workbook workbook;
        try (InputStream is = Files.newInputStream(tempFile)) {
            workbook = WorkbookFactory.create(is);
        }
        Sheet sheet = workbook.getSheetAt(0);

        List<String> numericalColumns = List.of( "spilled energy cost", "unsupplied energy cost","x", "y", "r", "g", "b");

        assertDoesNotThrow(() ->
                AreasValidator.checkNumericalColumns(
                        sheet,
                        "2036-2037",
                        numericalColumns, TrajectoryType.AREA.name()
                )
        );
    }

    @Test
    void shouldThrowWhenCellIsNotNumeric() throws Exception {
        tempFile = CreateExcelTestUtil.createExcelFile(
                tempDir,
                "InvalidNumericColumn.xlsx",
                "2036-2037",
                List.of("areas", "district", "spilled energy cost", "unsupplied energy cost", "x", "y", "r", "g", "b"),
                List.of(List.of("Area1", "district", "toto", "true", "abc", 425, 125, 230, 125)
                )
        );

        Workbook workbook;
        try (InputStream is = Files.newInputStream(tempFile)) {
            workbook = WorkbookFactory.create(is);
        }
        Sheet sheet = workbook.getSheetAt(0);

        // Numeric columns to verify
        List<String> numericalColumns = List.of("x", "y", "r", "g", "b");

        // --- Exécution ---
        BusinessException ex = assertThrows(BusinessException.class, () ->
                AreasValidator.checkNumericalColumns(
                        sheet,
                        "2036-2037",
                        numericalColumns,
                        TrajectoryType.AREA.name()
                )
        );

        // --- Vérifications ---
        String invalidCols = ex.getErrorMessageArguments().get(0);
        String invalidAreas = ex.getErrorMessageArguments().get(1);

        assertTrue(invalidCols.contains("x"));
        assertTrue(invalidAreas.contains("Area1"));
    }

    @Test
    void shouldThrowWhenCellIsNegativeForNumericColumns() throws Exception {
        tempFile = CreateExcelTestUtil.createExcelFile(
                tempDir,
                "NegativeCostColumn.xlsx",
                "2036-2037",
                List.of("areas", "spilled energy cost", "unsupplied energy cost", "x", "y", "r", "g", "b"),
                List.of(
                        List.of("Area1", -10.0, 200.0, 131, 425, 125, 230, 125),
                        List.of("Area2", 150.0, -5.0, 200, 300, 150, 210, 180)
                )
        );

        Workbook workbook;
        try (InputStream is = Files.newInputStream(tempFile)) {
            workbook = WorkbookFactory.create(is);
        }
        Sheet sheet = workbook.getSheetAt(0);

        List<String> numericalColumns = List.of("spilled energy cost", "unsupplied energy cost", "x", "y", "r", "g", "b");

        BusinessException ex = assertThrows(BusinessException.class, () ->
                AreasValidator.checkNumericalColumns(
                        sheet,
                        "2036-2037",
                        numericalColumns,
                        TrajectoryType.AREA.name()
                )
        );

        assertEquals("Waiting for positive Numeric values in {0} columns for area(s) {1}", ex.getMessage());
        String invalidCols = ex.getErrorMessageArguments().get(0);
        String invalidAreas = ex.getErrorMessageArguments().get(1);

        assertTrue(invalidCols.contains("spilled energy cost"));
        assertTrue(invalidCols.contains("unsupplied energy cost"));
        assertTrue(invalidAreas.contains("Area1"));
        assertTrue(invalidAreas.contains("Area2"));
    }

    @Test
    void testBusinessExceptionMessageTruncation() {

        String longValue = "Area".repeat(100); // 400 caractères

        BusinessException exception = BusinessException.builder()
                .message("Test message with long value: {0}")
                .errorMessageArguments(List.of(longValue))
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build();

        assertAll(
                () -> assertTrue(exception.getErrorMessageArguments().getFirst().length() <= 255),
                () -> assertTrue(exception.getErrorMessageArguments().getFirst().endsWith("...")),
                () -> assertEquals(255, exception.getErrorMessageArguments().getFirst().length()),
                () -> assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus())
        );
    }






}
