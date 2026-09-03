package com.rte_france.antares.datamanager_back.util.ExcelFilesValidators;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.util.CreateExcelTestUtil;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.ExcelCommonValidator;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.LinksValidator;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.ExcelFileType;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.LinksColumns;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LinksValidatorTest {
    @TempDir
    Path tempDir;

    private Path tempFile;


    @Test
    void testCheckForIdenticalDuplicateLinks() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "TestFile.xlsx", "2030-2031",
                List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                        "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                        "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                        "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                        "Flowbased_perimeter", "HVDC_MW_direct", "HVDC_MW_Indirect",
                        "HVDC_nb_direct", "HVDC_nb_indirect", "HVDC_FO_Rate_direct", "HVDC_FO_Rate_indirect"),
                List.of(
                        List.of("Area1-Area2", 100, 200, 150, 175, 300, 400, 250, 275, "TRUE", 50.0, 25.0, 2, 1, 1, 1),
                        List.of("Area1-Area2", 110, 210, 160, 185, 310, 410, 260, 285, "TRUE", 75.0, 30.0, 1, 1, 1, 1)
                )
        );
        BusinessException exception = assertThrows(BusinessException.class, () ->
                LinksValidator.linksDuplicateAndCellsValuesChecks(tempFile, ExcelFileType.LINKS, "2030-2031"));
        assertAll(
                () -> assertEquals("Duplicate value for {0}(s): {1} for {2} trajectory", exception.getMessage()),
                () -> assertIterableEquals(Arrays.asList("link", "Area1-Area2", TrajectoryType.LINK.name()), exception.getErrorMessageArguments()),
                () -> assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus())
        );
    }

    @Test
    void testCheckForSymmetricDuplicateLinks() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "TestFile.xlsx", "2030-2031",
                List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                        "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                        "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                        "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                        "Flowbased_perimeter", "HVDC_MW_direct", "HVDC_MW_Indirect",
                        "HVDC_nb_direct", "HVDC_nb_indirect", "HVDC_FO_Rate_direct", "HVDC_FO_Rate_indirect"),
                List.of(
                        List.of("Area3-Area4", 110, 210, 160, 185, 310, 410, 260, 285, "TRUE", 65.0, 80.0, 2, 1, 1, 1),
                        List.of("Area4-Area3", 110, 210, 160, 185, 310, 410, 260, 285, "TRUE", 65.0, 80.0, 2, 1, 1, 1)
                )
        );
        BusinessException exception = assertThrows(BusinessException.class, () ->
                LinksValidator.linksDuplicateAndCellsValuesChecks(tempFile, ExcelFileType.LINKS, "2030-2031"));
        assertAll(
                () -> assertEquals("Duplicate value in column 'Name' for horizon {0} in {1} trajectory. Values: {2} are considered identical", exception.getMessage()),
                () -> assertIterableEquals(Arrays.asList("2030-2031", TrajectoryType.LINK.name(), "Area3-Area4, Area4-Area3"), exception.getErrorMessageArguments()),
                () -> assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus())
        );
    }

    @Test
    void testCheckForInvalidColumnsNames() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "TestFile.xlsx", "2030-2031",
                List.of("name", "winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                        "Winter_HC_Direct_MW", "Winter_nb_Indirect_MW", // Invalid column name injected here
                        "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                        "rosa_Direct_MW", "Summer_hc_Indirect_MW",
                        "Flowbased_perimeter", "HVDC_MW_direct", "HVDC_MW_Indirect",
                        "HVDC_nb_direct", "HVDC_nb_indirect", "HVDC_FO_Rate_direct", "HVDC_FO_Rate_indirect"),
                List.of(
                        List.of("Area1/Area2", 100, 200, 150, 175, 300, 400, 250, 275, "TRUE", 50.0, 25.0, 2, 1, 1, 1)
                )
        );

        BusinessException exception = assertThrows(BusinessException.class, () ->
                ExcelCommonValidator.checkIfColumnsAreValid(tempFile, ExcelFileType.LINKS, "2030-2031", TrajectoryType.LINK.name()));

        Assertions.assertTrue(exception.getMessage().contains("Invalid column"));
    }

    @Test
    void testLinksFileIsOK() throws IOException {
        String horizon = "2035-2036";
        List<String> sheetNames = List.of(horizon, "parameters");

        List<List<String>> headers = List.of(
                List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                        "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                        "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                        "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                        "Flowbased_perimeter", "HVDC_MW_direct", "HVDC_MW_Indirect",
                        "HVDC_nb_direct", "HVDC_nb_indirect", "HVDC_FO_Rate_direct", "HVDC_FO_Rate_indirect"),
                List.of("Parameter", horizon)
        );

        List<List<List<?>>> rows = List.of(
                List.of(
                        List.of("AT/FR", 100, 200, 5, 150, 175, 300, 400, 250, false, 50.0, 25.0, 2, 1, 1, 1),
                        List.of("BE/GE", 110, 210, 160, 185, 310, 410, 260, 285, true, 75.0, 30.0, 1, 1, 1, 1)
                ),
                List.of(
                        List.of("hurdle_cost", 10.5),
                        List.of("HVDC", false)
                )
        );

        tempFile = CreateExcelTestUtil.createExcelFileWithTwoSheets(tempDir, "TestFile.xlsx", sheetNames, headers, rows);

        assertDoesNotThrow(() -> ExcelCommonValidator.checkIfColumnsAreValid(tempFile, ExcelFileType.LINKS, horizon, TrajectoryType.LINK.name()));
        assertDoesNotThrow(() -> LinksValidator.linksDuplicateAndCellsValuesChecks(tempFile, ExcelFileType.LINKS, horizon));
    }

    @Test
    void testLinksFileIsOKWithTrueAndFalseAsStrings() throws IOException {
        String horizon = "2035-2036";
        List<String> sheetNames = List.of(horizon, "parameters");

        List<List<String>> headers = List.of(
                List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                        "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                        "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                        "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                        "Flowbased_perimeter", "HVDC_MW_direct", "HVDC_MW_Indirect",
                        "HVDC_nb_direct", "HVDC_nb_indirect", "HVDC_FO_Rate_direct", "HVDC_FO_Rate_indirect"),
                List.of("Parameter", horizon)
        );

        List<List<List<?>>> rows = List.of(
                List.of(
                        List.of("AT-FR", 100, 200, 5, 150, 175, 300, 400, 250, "TRUE", 50.0, 25.0, 2, 1, 1, 1),
                        List.of("BE-GE", 110, 210, 160, 185, 310, 410, 260, 285, "true", 75.0, 30.0, 1, 1, 1, 1)
                ),
                List.of(
                        List.of("hurdle_cost", 10.5),
                        List.of("HVDC", "FALSE")
                )
        );

        tempFile = CreateExcelTestUtil.createExcelFileWithTwoSheets(tempDir, "TestFile.xlsx", sheetNames, headers, rows);

        assertDoesNotThrow(() -> ExcelCommonValidator.checkIfColumnsAreValid(tempFile, ExcelFileType.LINKS, horizon, TrajectoryType.LINK.name()));
        assertDoesNotThrow(() -> LinksValidator.linksDuplicateAndCellsValuesChecks(tempFile, ExcelFileType.LINKS, horizon));
    }


    @Test
    void testLinksFileContainsInvalidNumericValues() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "TestFile.xlsx", "2032-2033",
                List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                        "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                        "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                        "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                        "Flowbased_perimeter", "HVDC_MW_direct", "HVDC_MW_Indirect", "HVDC_nb_direct", "HVDC_nb_indirect", "HVDC_FO_Rate_direct", "HVDC_FO_Rate_indirect"),
                List.of(
                        List.of("ES-FR", "A", "B", 150, 175, 300, 400, 250, 275, 500, 60.0, 75.0, 1, 1, 1, 1),
                        List.of("ES-IT", 110, 210, "C", 185, 310, 410, 260, 285, 510, 65.0, 80.0, 2, 1, 1, 1)
                )
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> LinksValidator.linksDuplicateAndCellsValuesChecks(tempFile, ExcelFileType.LINKS, "2032-2033")
        );

        assertAll(
                () -> assertEquals("Waiting for Numeric Value(s) in column(s) {0} for link(s) {1} in LINK trajectory",
                        exception.getMessage()),
                () -> assertIterableEquals(
                        Arrays.asList("Winter_HC_Direct_MW, Winter_HP_Direct_MW, Winter_HP_Indirect_MW", "ES-FR, ES-IT"),
                        exception.getErrorMessageArguments()),

                () -> assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus())

        );

    }

    @Test
    void testLinksFileContainsPositiveNumericValues() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "TestFile.xlsx", "2032-2033",
                List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                        "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                        "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                        "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                        "Flowbased_perimeter", "HVDC_MW_direct", "HVDC_MW_Indirect", "HVDC_nb_direct", "HVDC_nb_indirect", "HVDC_FO_Rate_direct", "HVDC_FO_Rate_indirect"),
                List.of(
                        List.of("ES-FR", -100, -300, 150, 175, 300, 400, 250, 275, 500, 60.0, 75.0, 1, 1, 1, 1),
                        List.of("ES-IT", 110, 210, 20, 185, 310, -410, 260, 285, 510, 65.0, 80.0, 2, 1, 1, 1)
                )
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> LinksValidator.linksDuplicateAndCellsValuesChecks(tempFile, ExcelFileType.LINKS, "2032-2033")
        );

        assertAll(
                () -> assertEquals("Waiting for Positive Value(s) in column(s) {0} for link(s) {1} in LINK trajectory",
                        exception.getMessage()),
                () -> assertIterableEquals(
                        Arrays.asList("Summer_HP_Indirect_MW, Winter_HP_Direct_MW, Winter_HP_Indirect_MW", "ES-FR, ES-IT"),
                        exception.getErrorMessageArguments()),

                () -> assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus())
        );

    }

    @Test
    void testLinksFileContainsIntegerNumericValues() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "TestFile.xlsx", "2032-2033",
                List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                        "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                        "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                        "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                        "Flowbased_perimeter", "HVDC_MW_direct", "HVDC_MW_Indirect", "HVDC_nb_direct", "HVDC_nb_indirect", "HVDC_FO_Rate_direct", "HVDC_FO_Rate_indirect"),
                List.of(
                        List.of("ES-FR", 100.3, 300, 150, 175, 300, 400, -250, 275, "TRUE", 60.0, 75.0, 1, 1, 1, 1),
                        List.of("ES-IT", 110, 210, 20.2, 185, 310, 410, 260, -285, "FALSE", 65.0, 80.0, 2, 1, 1, 1)
                )
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> LinksValidator.linksDuplicateAndCellsValuesChecks(tempFile, ExcelFileType.LINKS, "2032-2033")
        );

        assertAll(
                () -> assertEquals("Waiting for Positive Value(s) in column(s) {0} for link(s) {1} in LINK trajectory",
                        exception.getMessage()),
                () -> assertIterableEquals(
                        Arrays.asList("Summer_HC_Direct_MW, Summer_HC_Indirect_MW", "ES-FR, ES-IT"),
                        exception.getErrorMessageArguments()),

                () -> assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus())

        );

    }

    @Test
    void testCheckPowerColumnsForZeroValues() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "TestFile.xlsx", "2030-2031",
                List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                        "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                        "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                        "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                        "Flowbased_perimeter", "HVDC_MW_direct", "HVDC_MW_Indirect", "HVDC_nb_direct", "HVDC_nb_indirect", "HVDC_FO_Rate_direct", "HVDC_FO_Rate_indirect"),
                List.of(
                        List.of("Area1/Area2", 0, 0, 0, 0, 0, 0, 0, 0, "TRUE", 0.0, 0.0, 0, 0, 0, 0),
                        List.of("Area3/Area4", 10, 20, 30, 40, 50, 60, 70, 80, "TRUE", 50.0, 25.0, 1, 1, 1, 1)
                )
        );

        List<String> parameterForWarning = LinksValidator.checkPowerColumnsForZeroValues(tempFile, "2030-2031");

        assertEquals(1, parameterForWarning.size());

    }

    @Test
    void testCheckIndirectForZeroValues() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "TestFile.xlsx", "2030-2031",
                List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                        "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                        "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                        "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                        "Flowbased_perimeter", "HVDC_MW_direct", "HVDC_MW_Indirect", "HVDC_nb_direct", "HVDC_nb_indirect", "HVDC_FO_Rate_direct", "HVDC_FO_Rate_indirect"),
                List.of(
                        List.of("Area1/Area2", 10, 0, 10, 0, 10, 0, 10, 0, "TRUE", 50.0, 0.0, 1, 0, 1, 0),
                        List.of("Area3/Area4", 10, 20, 30, 40, 50, 60, 70, 80, "TRUE", 50.0, 25.0, 1, 1, 1, 1)
                )
        );

        List<String> parameterForWarning = LinksValidator.areAllValuesZeroInGroup(tempFile, "2030-2031", LinksColumns.getIndirectColumnNames());


        assertEquals(1, parameterForWarning.size());
    }

    @Test
    void testCheckLinksAlphabeticalOrder() throws IOException {
        List<String> areasSavedForScenario = List.of("FR", "CH", "IT", "DE", "AT", "BE", "NL");
        tempFile = CreateExcelTestUtil.createExcelFile(
                tempDir,
                "TestFile.xlsx",
                "2030-2031",
                List.of("Name"),
                List.of(
                        List.of("FR-CH"), //should be CH-FR
                        List.of("IT-FR"), //should be FR-IT
                        List.of("DE-AT"), //should be AT-DE
                        List.of("BE-NL")
                )
        );


        List<String> warnings = LinksValidator.checkLinksAlphabeticalOrder(tempFile, "2030-2031", "Name", areasSavedForScenario);

        assertEquals(3, warnings.size());

    }

    @Test
    void shouldFailWhenInvalidBooleanValuesArePresentInLinks() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "InvalidBooleanLinks.xlsx", "2030-2031",
                List.of("Name", "Winter_HP_Direct_MW", "Flowbased_perimeter", "HVDC_MW_direct",
                        "HVDC_MW_Indirect", "HVDC_nb_direct"),
                List.of(
                        List.of("Link1-Link2", 100, "True", 50.0, 25.0, 2),
                        List.of("Link2-Link3", 200, "FalseBad", 75.0, 30.0, 1)
                )
        );

        Sheet sheet = WorkbookFactory.create(tempFile.toFile()).getSheet("2030-2031");
        List<String> booleanColumns = LinksColumns.getBooleanColumnNames();

        BusinessException exception = assertThrows(BusinessException.class, () ->
                ExcelCommonValidator.checkBooleanColumns(
                        sheet,
                        "2030-2031",
                        booleanColumns,
                        TrajectoryType.LINK.name()
                ));

        assertAll(
                () -> assertEquals("Waiting for boolean value(s) in column(s) {0} in {1} trajectory",
                        exception.getMessage()),
                () -> assertIterableEquals(
                        List.of(
                                "Flowbased_perimeter",
                                "LINK"
                        ),
                        exception.getErrorMessageArguments()),
                () -> assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus())

        );
    }

    @Test
    void shouldFailWhenEmptyMandatoryCellsArePresent() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "EmptyMandatoryCells.xlsx", "2037-2038",
                List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                        "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                        "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                        "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                        "Flowbased_perimeter", "HVDC_MW_direct", "HVDC_MW_Indirect", "HVDC_nb_direct", "HVDC_nb_indirect", "HVDC_FO_Rate_direct", "HVDC_FO_Rate_indirect"),
                List.of(
                        List.of("Area1/Area2", "", 0, "", 0, 10, 0, 10, 0, "TRUE", 50.0, 25.0, 1, 1, 1, 1),
                        List.of("Area3/Area4", 10, 20, 30, 40, "", 60, 70, 80, "TRUE", 50.0, 25.0, 1, 1, 1, 1),
                        List.of("Area5/Area6", 10, 20, 30, 40, 200, 60, 70, 80, "TRUE", 50.0, 25.0, 1, 1, 1, 1)
                )
        );

        BusinessException exception = assertThrows(BusinessException.class, () ->
                ExcelCommonValidator.checkIfColumnsAreValid(tempFile, ExcelFileType.LINKS, "2037-2038", TrajectoryType.LINK.name()));

        assertAll(
                () -> assertEquals("Empty values found for {0}(s): {1} for horizon {2} in {3} trajectory",
                        exception.getMessage()),
                () -> assertIterableEquals(
                        List.of(TrajectoryType.LINK.name().toLowerCase(), "Area1/Area2, Area3/Area4", "2037-2038", "LINK"),
                        exception.getErrorMessageArguments()),
                () -> assertEquals(HttpStatus.BAD_REQUEST,
                        exception.getHttpStatus())
        );
    }

    @Test
    void shouldFailWhenHvdcInParametersSheetIsInvalid() throws IOException {
        String horizon = "2030-2031";

        List<String> sheetNames = List.of(horizon, "parameters");

        List<List<String>> headers = List.of(
                List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                        "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                        "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                        "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                        "Flowbased_perimeter", "HVDC_MW_direct", "HVDC_MW_Indirect",
                        "HVDC_nb_direct", "HVDC_nb_indirect", "HVDC_FO_Rate_direct", "HVDC_FO_Rate_indirect"),
                // Headers for the parameters sheet (Row 0)
                List.of("Parameter", horizon)
        );

        List<List<List<?>>> rows = List.of(
                List.of(
                        List.of("Area1-Area2", 100, 200, 150, 175, 300, 400, 250, 275, "TRUE", 50.0, 25.0, 2, 1, 1, 1)
                ),
                // Data for the parameters sheet (Starting at Row 1)
                List.of(
                        List.of("hurdle_cost", 10.5),           // Row 1
                        List.of("HVDC", "NotABoolean")          // Row 2
                )
        );

        tempFile = CreateExcelTestUtil.createExcelFileWithTwoSheets(
                tempDir,
                "InvalidHvdc.xlsx",
                sheetNames,
                headers,
                rows
        );

        BusinessException exception = assertThrows(BusinessException.class, () ->
                LinksValidator.linksDuplicateAndCellsValuesChecks(tempFile, ExcelFileType.LINKS, horizon));

        assertAll(
                () -> assertEquals("Waiting for boolean value(s) in column(s) {0} in {1} trajectory", exception.getMessage()),
                () -> assertIterableEquals(List.of("HVDC", TrajectoryType.LINK.name()), exception.getErrorMessageArguments()),
                () -> assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus())
        );
    }

    @Test
    void shouldNotFailWhenHvdcColumnsAreEmpty() throws IOException {
        String horizon = "2030-2031";
        List<String> sheetNames = List.of(horizon, "parameters");

        List<List<String>> headers = List.of(
                List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                        "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                        "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                        "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                        "Flowbased_perimeter", "HVDC_MW_direct", "HVDC_MW_Indirect",
                        "HVDC_nb_direct", "HVDC_nb_indirect", "HVDC_FO_Rate_direct", "HVDC_FO_Rate_indirect"),
                List.of("Parameter", horizon)
        );

        List<List<List<?>>> rows = List.of(
                List.of(
                        List.of("AT-CH", 100, 200, 150, 175, 300, 400, 250, 275, "TRUE", "", "", "", "", "", ""),
                        List.of("AT-CZ", 110, 210, 160, 185, 310, 410, 260, 285, "TRUE", 50.0, 25.0, 1, 1, 1, 1)
                ),
                List.of(
                        List.of("hurdle_cost", 10.5),
                        List.of("HVDC", true)
                )
        );

        tempFile = CreateExcelTestUtil.createExcelFileWithTwoSheets(tempDir, "EmptyHvdc.xlsx", sheetNames, headers, rows);

        assertDoesNotThrow(() ->
                LinksValidator.linksDuplicateAndCellsValuesChecks(tempFile, ExcelFileType.LINKS, horizon));
    }

    @Test
    void shouldFailWhenNonHvdcNumericColumnsAreEmpty() throws IOException {
        String horizon = "2030-2031";
        List<String> sheetNames = List.of(horizon, "parameters");

        List<List<String>> headers = List.of(
                List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                        "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                        "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                        "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                        "Flowbased_perimeter", "HVDC_MW_direct", "HVDC_MW_Indirect",
                        "HVDC_nb_direct", "HVDC_nb_indirect", "HVDC_FO_Rate_direct", "HVDC_FO_Rate_indirect"),
                List.of("Parameter", horizon)
        );

        List<List<List<?>>> rows = List.of(
                List.of(
                        List.of("AT-CH", "", 200, 150, 175, 300, 400, 250, 275, "TRUE", 50.0, 25.0, 1, 1, 1, 1)
                ),
                List.of(
                        List.of("hurdle_cost", 10.5),
                        List.of("HVDC", true)
                )
        );

        tempFile = CreateExcelTestUtil.createExcelFileWithTwoSheets(tempDir, "EmptyNumeric.xlsx", sheetNames, headers, rows);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                LinksValidator.linksDuplicateAndCellsValuesChecks(tempFile, ExcelFileType.LINKS, horizon));

        assertTrue(exception.getMessage().contains("Waiting for Numeric Value"));
        assertTrue(exception.getErrorMessageArguments().contains("Winter_HP_Direct_MW"));
    }

    @Test
    void shouldFailWhenFlowbasedPerimeterIsEmpty() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "EmptyFlowbased.xlsx", "2037-2038",
                List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                        "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                        "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                        "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                        "Flowbased_perimeter", "HVDC_MW_direct", "HVDC_MW_Indirect",
                        "HVDC_nb_direct", "HVDC_nb_indirect", "HVDC_FO_Rate_direct", "HVDC_FO_Rate_indirect"),
                List.of(
                        List.of("Area1-Area2", 100, 200, 150, 175, 300, 400, 250, 275, "", 50.0, 25.0, 1, 1, 1, 1)
                )
        );

        BusinessException exception = assertThrows(BusinessException.class, () ->
                ExcelCommonValidator.checkIfColumnsAreValid(tempFile, ExcelFileType.LINKS, "2037-2038", TrajectoryType.LINK.name()));

        assertTrue(exception.getMessage().contains("Empty values found"));
    }

    @Test
    void shouldFailWhenFlowbasedPerimeterIsInvalid() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "InvalidFlowbased.xlsx", "2037-2038",
                List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                        "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                        "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                        "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                        "Flowbased_perimeter", "HVDC_MW_direct", "HVDC_MW_Indirect",
                        "HVDC_nb_direct", "HVDC_nb_indirect", "HVDC_FO_Rate_direct", "HVDC_FO_Rate_indirect"),
                List.of(
                        List.of("Area1-Area2", 100, 200, 150, 175, 300, 400, 250, 275, "NotABoolean", 50.0, 25.0, 1, 1, 1, 1)
                )
        );

        BusinessException exception = assertThrows(BusinessException.class, () ->
                LinksValidator.linksDuplicateAndCellsValuesChecks(tempFile, ExcelFileType.LINKS, "2037-2038"));

        assertTrue(exception.getMessage().contains("Waiting for boolean value"));
    }

    @Test
    void testLinksFileIsOK_withLINK_ME_trajectoryType() throws IOException {
        String horizon = "2035-2036";
        List<String> sheetNames = List.of("2036", "parameters");

        tempFile = CreateExcelTestUtil.createExcelFileWithTwoSheets(tempDir, "TestFile.xlsx", sheetNames,
                List.of(
                        List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                                "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                                "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                                "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                                "Flowbased_perimeter", "HVDC_MW_direct", "HVDC_MW_Indirect",
                                "HVDC_nb_direct", "HVDC_nb_indirect", "HVDC_FO_Rate_direct", "HVDC_FO_Rate_indirect"),
                        List.of() // parameters sheet (empty for this test)
                ),
                List.of(
                        List.of(
                                List.of("Area1-Area2", 100, 200, 150, 175, 300, 400, 250, 275, "TRUE", 50.0, 25.0, 2, 1, 1, 1)
                        ),
                        List.of() // parameters sheet rows (empty for this test)
                )
        );

        assertDoesNotThrow(() -> LinksValidator.linksDuplicateAndCellsValuesChecks(tempFile, ExcelFileType.LINKS, horizon, TrajectoryType.LINK_ME));
    }
}
