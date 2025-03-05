package com.rte_france.antares.datamanager_back.util;

import com.rte_france.antares.datamanager_back.exception.TechnicalAntaresDataMangerException;
import com.rte_france.antares.datamanager_back.util.columnsEnums.ExcelFileType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.util.AssertionErrors.assertTrue;

class ExcelLinksFileValidatorTest {
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
        TechnicalAntaresDataMangerException exception = assertThrows(TechnicalAntaresDataMangerException.class, () ->
                ExcelFileValidator.checkIfColumnsAreValid(tempFile, ExcelFileType.LINKS, "2030-2031"));
        Assertions.assertTrue(exception.getMessage().contains("Duplicate value 'Area1/Area2'"));
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

        TechnicalAntaresDataMangerException exception =assertThrows(TechnicalAntaresDataMangerException.class, () ->
                ExcelFileValidator.checkIfColumnsAreValid(tempFile, ExcelFileType.LINKS, "2030-2031"));

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
        ExcelFileValidator.checkIfColumnsAreValid(tempFile, ExcelFileType.LINKS, "2035-2036");
        assertDoesNotThrow(() -> ExcelFileValidator.checkIfColumnsAreValid(tempFile, ExcelFileType.LINKS, "2035-2036"));
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
        ExcelFileValidator.checkIfColumnsAreValid(tempFile, ExcelFileType.LINKS, "2035-2036");
        assertDoesNotThrow(() -> ExcelFileValidator.checkIfColumnsAreValid(tempFile, ExcelFileType.LINKS, "2035-2036"));
    }

    @Test
    void testLinksFileIsOKOOOO() throws IOException {
        // Modify the file to include an invalid numeric value (negative or non-integer)
        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "TestFile.xlsx", "2032-2033",
                List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                        "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                        "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                        "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                        "Flowbased_perimeter", "HVDC", "Specific_TS", "Forced_Outage_HVAC"),
                List.of(
                        List.of("Area1", -100, 200, 150, 175, 300, 400, 250, 275, 500, 60, 75, 90), // Invalid negative value
                        List.of("Area2", 110, 210, 160, 185, 310, 410, 260, 285, 510, 65, 80, 95)
                )
        );


        assertThrows(TechnicalAntaresDataMangerException.class, () -> ExcelFileValidator.checkIfColumnsAreValid(tempFile, ExcelFileType.LINKS, "2032-2033"));
    }

}
