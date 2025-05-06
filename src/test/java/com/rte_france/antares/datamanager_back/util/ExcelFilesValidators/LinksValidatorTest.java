package com.rte_france.antares.datamanager_back.util.ExcelFilesValidators;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.util.CreateExcelTestUtil;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.ExcelCommonValidator;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.LinksValidator;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.ExcelFileType;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.LinksColumns;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LinksValidatorTest {
    @TempDir
    Path tempDir;

    private Path tempFile;


    @Test
    void testCheckForDuplicateLinks() throws IOException {

        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "TestFile.xlsx", "2030-2031",
                List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                        "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                        "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                        "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                        "Flowbased_perimeter", "HVDC", "Specific_TS", "Forced_Outage_HVAC"),
                List.of(
                        List.of("Area1/Area2", 100, 200, 150, 175, 300, 400, 250, 275, 500, 60, 75, 90),
                        List.of("Area1/Area2", 110, 210, 160, 185, 310, 410, 260, 285, 510, 65, 80, 95)
                )
        );
        BusinessException exception = assertThrows(BusinessException.class, () ->
                LinksValidator.linksDuplicateAndCellsValuesChecks(tempFile, ExcelFileType.LINKS, "2030-2031"));
        Assertions.assertTrue(exception.getMessage().contains("Duplicate value {0}"));
    }
    @Test
    void testCheckForInvalidColumnsNames() throws IOException {

        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "TestFile.xlsx", "2030-2031",
                List.of("name", "winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                        "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                        "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                        "Summer_HC_Direct_MW", "Summer_hc_Indirect_MW",
                        "Flowbased_perimeter", "HVDC", "Specific_TS", "Forced_Outage_HVAC"),
                List.of(
                        List.of("Area1/Area2", 100, 200, 150, 175, 300, 400, 250, 275, 500, 60, 75, 90),
                        List.of("Area1/Area2", 110, 210, 160, 185, 310, 410, 260, 285, 510, 65, 80, 95)
                )
        );

        BusinessException exception =assertThrows(BusinessException.class, () ->
                ExcelCommonValidator.checkIfColumnsAreValid(tempFile, ExcelFileType.LINKS, "2030-2031"));

        Assertions.assertTrue(exception.getMessage().contains("Invalid column"));
    }

    @Test
    void testLinksFileIsOK() throws IOException {

        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "TestFile.xlsx", "2035-2036",
                List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                        "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                        "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                        "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                        "Flowbased_perimeter", "HVDC", "Specific_TS", "Forced_Outage_HVAC"),
                List.of(
                        List.of("AT/FR", 100, 200, 5, 150, 175, 300, 400, 250, false, false, true, false),
                        List.of("BE/GE", 110, 210, 160, 185, 310, 410, 260, 285, true,true, true, false)
                )
        );
        ExcelCommonValidator.checkIfColumnsAreValid(tempFile, ExcelFileType.LINKS, "2035-2036");
        LinksValidator.linksDuplicateAndCellsValuesChecks(tempFile, ExcelFileType.LINKS, "2035-2036");
        assertDoesNotThrow(() -> ExcelCommonValidator.checkIfColumnsAreValid(tempFile, ExcelFileType.LINKS, "2035-2036"));
    }

    @Test
    void testLinksFileIsOKWithTrueAndFalseAsStrings() throws IOException {

        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "TestFile.xlsx", "2035-2036",
                List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                        "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                        "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                        "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                        "Flowbased_perimeter", "HVDC", "Specific_TS", "Forced_Outage_HVAC"),
                List.of(
                        List.of("AT/FR", 100, 200, 5, 150, 175, 300, 400, 250, "TRUE", "false", "true", "false"),
                        List.of("BE/GE", 110, 210, 160, 185, 310, 410, 260, 285, "true","FALSE", "true", "false")
                )
        );
        LinksValidator.linksDuplicateAndCellsValuesChecks(tempFile, ExcelFileType.LINKS, "2035-2036");
        assertDoesNotThrow(() -> ExcelCommonValidator.checkIfColumnsAreValid(tempFile, ExcelFileType.LINKS, "2035-2036"));
    }


    @Test
    void testLinksFileContainsInvalidNumericValues() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "TestFile.xlsx", "2032-2033",
                List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                        "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                        "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                        "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                        "Flowbased_perimeter", "HVDC", "Specific_TS", "Forced_Outage_HVAC"),
                List.of(
                        List.of("Area1", 100.25, -200, 150, 175, 300, 400, 250, 275, 500, 60, 75, 90),
                        List.of("Area2", 110, 210, 160, 185, 310, 410, 260, 285, 510, 65, 80, 95)
                )
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> LinksValidator.linksDuplicateAndCellsValuesChecks(tempFile, ExcelFileType.LINKS, "2032-2033")
        );

        String errorMessage = exception.getMessage();

        assertTrue(errorMessage.contains("Invalid numeric values"), "Invalid numeric values in sheet {0} in file: {1}. Details: {2}");
    }

    @Test
    void testCheckPowerColumnsForZeroValues() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "TestFile.xlsx", "2030-2031",
                List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                        "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                        "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                        "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                        "Flowbased_perimeter", "HVDC", "Specific_TS", "Forced_Outage_HVAC"),
                List.of(
                        List.of("Area1/Area2", 0, 0, 0, 0, 0, 0, 0, 0, "TRUE", "FALSE", "TRUE", "FALSE"),
                        List.of("Area3/Area4", 10, 20, 30, 40, 50, 60, 70, 80, "TRUE", "FALSE", "TRUE", "FALSE")
                )
        );

        List<String> parameterForWarning = LinksValidator.checkPowerColumnsForZeroValues(tempFile, "2030-2031");

        assertEquals(1, parameterForWarning.size());
        assertTrue(parameterForWarning.get(0).contains("TestFile.xlsx"));

    }

    @Test
    void testCheckIndirectForZeroValues() throws IOException {
        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "TestFile.xlsx", "2030-2031",
                List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                        "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                        "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                        "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                        "Flowbased_perimeter", "HVDC", "Specific_TS", "Forced_Outage_HVAC"),
                List.of(
                        List.of("Area1/Area2", 10, 0, 10, 0, 10, 0, 10, 0, "TRUE", "FALSE", "TRUE", "FALSE"),
                        List.of("Area3/Area4", 10, 20, 30, 40, 50, 60, 70, 80, "TRUE", "FALSE", "TRUE", "FALSE")
                )
        );

        List<String> parameterForWarning = LinksValidator.areAllValuesZeroInGroup(tempFile, "2030-2031", LinksColumns.getIndirectColumnNames());


        assertEquals(1, parameterForWarning.size());
        assertTrue(parameterForWarning.get(0).contains("TestFile.xlsx"));
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


}
