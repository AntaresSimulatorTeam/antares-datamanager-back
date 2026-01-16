package com.rte_france.antares.datamanager_back.util.ExcelFilesValidators;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.ExcelCommonValidator;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.util.CreateExcelTestUtil;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExcelCommonValidatorTest {
    @TempDir
    Path tempDir;

    private Path tempFile;

    @Test
    void shouldNotThrowWhenAllNumeric() throws Exception {
        tempFile = CreateExcelTestUtil.createExcelFile(
                tempDir,
                "ValidNumericColumns.xlsx",
                "2036-2037",
                // En-têtes
                List.of("Areas", "Power to gas", "Stockage court terme", "x", "y", "r", "g", "b"),
                List.of(
                        List.of("Area1", "false", "true", 131, 425, 125, 230, 125),
                        List.of("Area2", "false", "true", 200, 300, 150, 210, 180)
                )
        );

        Workbook workbook;
        try (InputStream is = Files.newInputStream(tempFile)) {
            workbook = WorkbookFactory.create(is);
        }
        Sheet sheet = workbook.getSheetAt(0);

        List<String> numericalColumns = List.of("x", "y", "r", "g", "b");

        assertDoesNotThrow(() ->
                ExcelCommonValidator.checkNumericalColumns(
                        sheet,
                        "2036-2037",
                        numericalColumns,
                        "Stockage court terme"
                )
        );
    }

    @Test
    void shouldThrowWhenCellIsNotNumeric() throws Exception {
        tempFile = CreateExcelTestUtil.createExcelFile(
                tempDir,
                "InvalidNumericColumn.xlsx",
                "2036-2037",
                List.of("", "", "Stockage court terme", "x", "y", "r", "g", "b"),
                List.of(
                        List.of("Area1", "false", "true", "abc", 425, 125, 230, 125)
                )
        );

        // --- Lecture du fichier Excel ---
        Workbook workbook;
        try (InputStream is = Files.newInputStream(tempFile)) {
            workbook = WorkbookFactory.create(is);
        }
        Sheet sheet = workbook.getSheetAt(0);

        // Colonnes numériques à vérifier
        List<String> numericalColumns = List.of("x", "y", "r", "g", "b");

        // --- Exécution ---
        BusinessException ex = assertThrows(BusinessException.class, () ->
                ExcelCommonValidator.checkNumericalColumns(
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
    void shouldThrowWhenMultipleRowsAndColumnsInvalid() throws Exception {
        tempFile = CreateExcelTestUtil.createExcelFile(
                tempDir,
                "InvalidColumns.xlsx",
                "2036-2037",
                // En-têtes
                List.of("", "", "Stockage court terme", "x", "y", "r", "g", "b"),
                // Données
                List.of(
                        List.of("Area1", "false", "true", "abc", 425, "", 230, 125),
                        List.of("Area2", "false", "true", 131, "xyz", 125, 230, 125)
                )
        );

        Workbook workbook;
        try (InputStream is = Files.newInputStream(tempFile)) {
            workbook = WorkbookFactory.create(is);
        }
        Sheet sheet = workbook.getSheetAt(0);

        // Colonnes numériques à vérifier
        List<String> numericalColumns = List.of("x", "y", "r", "g", "b");

        // --- Exécution ---
        BusinessException ex = assertThrows(BusinessException.class, () ->
                ExcelCommonValidator.checkNumericalColumns(
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
        assertTrue(invalidCols.contains("y"));
        assertTrue(invalidCols.contains("r"));

        assertTrue(invalidAreas.contains("Area1"));
        assertTrue(invalidAreas.contains("Area2"));
    }

    @Test
    void shouldThrowWhenCellIsNull() throws Exception {
        tempFile = CreateExcelTestUtil.createExcelFile(
                tempDir,
                "NullNumericColumn.xlsx",
                "2036-2037",
                List.of("", "", "Stockage court terme", "x", "y", "r", "g", "b"),
                List.of(
                        List.of("Area1", "false", "true", "", 425, 125, 230, 125)
                )
        );

        // --- Lecture du fichier Excel ---
        Workbook workbook;
        try (InputStream is = Files.newInputStream(tempFile)) {
            workbook = WorkbookFactory.create(is);
        }
        Sheet sheet = workbook.getSheetAt(0);

        // Colonnes numériques à vérifier
        List<String> numericalColumns = List.of("x", "y", "r", "g", "b");

        // --- Exécution ---
        BusinessException ex = assertThrows(BusinessException.class, () ->
                ExcelCommonValidator.checkNumericalColumns(
                        sheet,
                        "2036-2037",
                        numericalColumns,
                        TrajectoryType.AREA.name()
                )
        );

        String invalidCols = ex.getErrorMessageArguments().get(0);
        String invalidAreas = ex.getErrorMessageArguments().get(1);

        assertTrue(invalidCols.contains("x"));
        assertTrue(invalidAreas.contains("Area1"));
    }

    @Test
    void shouldThrowWithCorrectInvalidColumnsAndAreas() throws Exception {
        tempFile = CreateExcelTestUtil.createExcelFile(
                tempDir,
                "MultipleInvalidColumns.xlsx",
                "2036-2037",
                List.of("", "", "Stockage court terme", "x", "y", "r", "g", "b"),
                List.of(
                        List.of("Area1", "false", "true", "abc", 425, "", 23, 125),
                        List.of("Area2", "false", "true", 131, "xyz", 125, 230, 125)
                )
        );

        // --- Lecture du fichier Excel ---
        Workbook workbook;
        try (InputStream is = Files.newInputStream(tempFile)) {
            workbook = WorkbookFactory.create(is);
        }
        Sheet sheet = workbook.getSheetAt(0);

        // Colonnes numériques à vérifier
        List<String> numericalColumns = List.of("x", "y", "r", "g", "b");

        // --- Exécution ---
        BusinessException ex = assertThrows(BusinessException.class, () ->
                ExcelCommonValidator.checkNumericalColumns(
                        sheet,
                        "2036-2037",
                        numericalColumns,
                        TrajectoryType.AREA.name()
                )
        );

        // --- Vérifications ---
        String invalidCols = ex.getErrorMessageArguments().get(0);
        String invalidAreas = ex.getErrorMessageArguments().get(1);

        // Colonnes invalides attendues : x, y, r
        assertTrue(invalidCols.contains("x"));
        assertTrue(invalidCols.contains("y"));
        assertTrue(invalidCols.contains("r"));

        // Zones invalides attendues : Area1, Area2
        assertTrue(invalidAreas.contains("Area1"));
        assertTrue(invalidAreas.contains("Area2"));
    }

}
