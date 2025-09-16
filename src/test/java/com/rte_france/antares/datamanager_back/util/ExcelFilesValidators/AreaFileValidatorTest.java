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

        ExcelCommonValidator.checkIfColumnsAreValid(tempFile, ExcelFileType.AREAS, "2035-2036", TrajectoryType.AREA.name());
    }

    @Test
    void shouldFailWhenColumnNamesAreInvalid() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "InvalidColumns.xlsx", "2036-2037",
                List.of("areastt", "Gas Power", "Storage", "x", "y", "r", "g", "b"),
                List.of(
                        List.of("Area1", "false", "true", "131", "425", "125", "230", "125")
                )
        );

        BusinessException exception = assertThrows(BusinessException.class, () ->
                ExcelCommonValidator.checkIfColumnsAreValid(tempFile, ExcelFileType.AREAS, "2036-2037", TrajectoryType.AREA.name()));

        assertAll(
                () -> assertEquals(
                        "Invalid column(s) name(s): areastt, Gas Power, Storage for horizon {0} in {1} trajectory",
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
                List.of("", "", "Stockage court terme", "x", "y", "r", "g", "b"),
                List.of(
                        List.of("Area1", "false", "true", "131", "425", "125", "230", "125")
                )
        );

        BusinessException exception = assertThrows(BusinessException.class, () ->
                ExcelCommonValidator.checkIfColumnsAreValid(tempFile, ExcelFileType.AREAS, "2036-2037", TrajectoryType.AREA.name()));

        assertAll(
                () -> assertEquals(
                        "Columns: areas, Power To Gas not found for horizon {0} in {1} trajectory",
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
                List.of("areas", "Power To Gas", "Stockage court terme", "x", "y", "r", "g", "b"),
                List.of(
                        List.of("B1", "10", "", "", "4", "1", "2", "3"),
                        List.of("A2", "10", "", "", "4", "1", "2", "3")
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
    void checkStringColumnsShouldThrowExceptionWhenDataIsInvalid() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "ErrorFile.xlsx", "2035-2036",
                List.of("areas", "Power To Gas", "Stockage court terme", "x", "y", "r", "g", "b"),
                List.of(
                        List.of(123, "True", "True", "230", "420", "128", "260", "113"),
                        List.of(456, "False", "True", "168", "650", "125", "265", "113")
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
    void shouldFailWhenInvalidBooleanValuesArePresent() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "InvalidBooleans.xlsx", "2037-2038",
                List.of("areas", "Power To Gas", "Stockage court terme", "x", "y", "r", "g", "b"),
                List.of(
                        List.of("Area2", 420, "360", "230", "420", "128", "260", "113"),
                        List.of("Area3", "too", "True", "168", "650", "125", "265", "113")
                )
        );

        Sheet sheet = WorkbookFactory.create(tempFile.toFile()).getSheet("2037-2038");
        List<String> booleanColumns = List.of("Power To Gas", "Stockage court terme");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                ExcelCommonValidator.checkBooleanColumns(
                        sheet,
                        "2037-2038",
                        booleanColumns,
                        TrajectoryType.AREA.name()
                ));

        assertAll(
                () -> assertEquals("Waiting for boolean value(s) in column(s) {0} in {1} trajectory",
                        exception.getMessage()),
                () -> assertIterableEquals(
                        List.of(
                                "Power To Gas, Stockage court terme",
                                "AREA"),
                        exception.getErrorMessageArguments()),
                () -> assertEquals(HttpStatus.BAD_REQUEST,
                        exception.getHttpStatus())
        );
    }

    @Test
    void checkAreasValuesLengthShouldThrowExceptionWhenValueIsTooLong() throws IOException {

        String longAreaName = "aBcDeFgHiJkLmNoPqR";
        String longAreaName2 = "aBcDeFgHiJkLmNoPqR56";


        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "TooLongArea.xlsx", "2035-2036",
                List.of("areas", "Power To Gas", "Stockage court terme", "x", "y", "r", "g", "b"),
                List.of(
                        List.of(longAreaName, "True", "True", "230", "420", "128", "260", "113"),
                        List.of(longAreaName2, "False", "True", "168", "650", "125", "265", "113")
                )
        );

        try (Workbook workbook = WorkbookFactory.create(Files.newInputStream(tempFile))) {
            Sheet sheet = workbook.getSheet("2035-2036");

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> AreasValidator.checkAreasValuesLength(sheet, "2035-2036", "areas"));

            assertAll(
                    () -> assertEquals("Value too long for area(s) : {0} in {1} trajectory",
                            exception.getMessage()),
                    () -> assertIterableEquals(
                            List.of(
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
    void checkIfColumnsAreValid_shouldThrowBusinessException_whenSheetIsMissing() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "test_sheet.xlsx", "missing",
                List.of("areas", "Power To Gas", "Stockage court terme", "x", "y", "r", "g", "b"),
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
                List.of("areas", "Power To Gas", "Stockage court terme", "x", "y", "r", "g", "b"),
                List.of(
                        List.of("Area1", "True", "True", "230", "420", "128", "260", "113"),
                        List.of("Area1", "False", "True", "168", "650", "125", "265", "113"),
                        List.of("Area2", "False", "True", "168", "650", "125", "265", "113"),
                        List.of("Area2", "False", "True", "168", "650", "125", "265", "113")
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
