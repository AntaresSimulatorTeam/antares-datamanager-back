package com.rte_france.antares.datamanager_back.util.excel_file_validators;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExcelCommonValidatorTest {

    @TempDir
    Path tempDir;

    @Test
    void checkNumericDataCMorMR_ShouldHandleSemicolonWithCommaDecimal() throws IOException {
        Path csvFile = tempDir.resolve("test_semicolon_valid.csv");
        Files.write(csvFile, List.of(
                "DATE_HEURE;HEURE;Cluster1;Cluster2",
                "2024-01-01;01:00;10,5;20,0"
        ));

        assertDoesNotThrow(() -> ExcelCommonValidator.checkNumericDataCMorMR(csvFile, "traj1", "CM"));
    }

    @Test
    void checkNumericDataCMorMR_ShouldHandleCommaDelimiterWithDotDecimal() throws IOException {
        Path csvFile = tempDir.resolve("test_comma_valid.csv");
        Files.write(csvFile, List.of(
                "DATE_HEURE,HEURE,Cluster1,Cluster2",
                "2024-01-01,01:00,10.5,20.0"
        ));

        assertDoesNotThrow(() -> ExcelCommonValidator.checkNumericDataCMorMR(csvFile, "traj1", "CM"));
    }

    @Test
    void checkNumericDataCMorMR_ShouldCorrectlyParseSemicolonWithCommaDecimal() throws IOException {
        Path csvFile = tempDir.resolve("test_semicolon_numeric_fail.csv");
        Files.write(csvFile, List.of(
                "DATE_HEURE;HEURE;Cluster1;Cluster2",
                "2024-01-01;01:00;10,5;NOT_A_NUMBER"
        ));

        // If the semicolon is correctly detected, it should identify "NOT_A_NUMBER" as invalid.
        assertThrows(BusinessException.class, () -> ExcelCommonValidator.checkNumericDataCMorMR(csvFile, "traj1", "CM"));
    }

}
