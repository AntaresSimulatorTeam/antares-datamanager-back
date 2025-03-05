package com.rte_france.antares.datamanager_back.util;

import com.rte_france.antares.datamanager_back.exception.TechnicalAntaresDataMangerException;
import com.rte_france.antares.datamanager_back.util.columnsEnums.ExcelFileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ExcelLinksFileValidatorTest {
    @TempDir
    Path tempDir;

    private Path tempFile;

    @BeforeEach
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);

        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "TestFile.xlsx", "2030-2031",
                List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                        "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                        "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                        "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                        "Flowbased_perimeter", "HVDC", "Specific_TS", "Forced_Outage_HVAC"),
                List.of(
                        List.of("Area1", 100, 200, 150, 175, 300, 400, 250, 275, 500, 60, 75, 90),
                        List.of("Area2", 110, 210, 160, 185, 310, 410, 260, 285, 510, 65, 80, 95)
                )
        );
    }

    @Test
    void testCheckForDuplicateValues() throws IOException {

        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "TestFile.xlsx", "2030-2031",
                List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
                        "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
                        "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
                        "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
                        "Flowbased_perimeter", "HVDC", "Specific_TS", "Forced_Outage_HVAC"),
                List.of(
                        List.of("Area1/Area2", 100, 200, 150, 175, 300, 400, 250, 275, 500, 60, 75, 90),
                        List.of("Area1/Area2", 110, 210, 160, 185, 310, 410, 260, 285, 510, 65, 80, 95)  // Duplicate "Area1"
                )
        );

        assertThrows(TechnicalAntaresDataMangerException.class, () ->
                ExcelFileValidator.checkIfColumnsAreValid(tempFile, ExcelFileType.LINKS, "2030-2031"));
    }

//    @Test
//    void testCheckNumericColumns() {
//        // Use the method of LinkFileProcessor to test numeric validation
//        LinkFileProcessor processor = new LinkFileProcessor();
//
//        // Modify file to contain valid numeric values
//        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "TestFile.xlsx", "2030-2031",
//                List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
//                        "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
//                        "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
//                        "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
//                        "Flowbased_perimeter", "HVDC", "Specific_TS", "Forced_Outage_HVAC"),
//                List.of(
//                        List.of("Area1", 100, 200, 150, 175, 300, 400, 250, 275, 500, 60, 75, 90),
//                        List.of("Area2", 110, 210, 160, 185, 310, 410, 260, 285, 510, 65, 80, 95)
//                )
//        );
//
//        // Check if all numeric values are valid
//        assertDoesNotThrow(() -> processor.processLinkFile(tempFile, "2030-2031"));
//
//        // Modify the file to include an invalid numeric value (negative or non-integer)
//        tempFile = CreateExcelTestUtil.createExcelFile(tempDir, "TestFile.xlsx", "2030-2031",
//                List.of("Name", "Winter_HP_Direct_MW", "Winter_HP_Indirect_MW",
//                        "Winter_HC_Direct_MW", "Winter_HC_Indirect_MW",
//                        "Summer_HP_Direct_MW", "Summer_HP_Indirect_MW",
//                        "Summer_HC_Direct_MW", "Summer_HC_Indirect_MW",
//                        "Flowbased_perimeter", "HVDC", "Specific_TS", "Forced_Outage_HVAC"),
//                List.of(
//                        List.of("Area1", -100, 200, 150, 175, 300, 400, 250, 275, 500, 60, 75, 90), // Invalid negative value
//                        List.of("Area2", 110, 210, 160, 185, 310, 410, 260, 285, 510, 65, 80, 95)
//                )
//        );
//
//        // It should throw an exception due to invalid numeric values
//        assertThrows(TechnicalAntaresDataMangerException.class, () -> processor.processLinkFile(tempFile, "2030-2031"));
//    }

}
