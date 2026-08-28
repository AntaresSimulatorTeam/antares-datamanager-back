package com.rte_france.antares.datamanager_back.util;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UtilsGetCellValueTest {

    @Test
    void returnsNullWhenCellMissing() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("S");
            Row row = sh.createRow(0);
            // Do not create any cell at index 5
            Object v = Utils.getCellValue(row, 5);
            assertNull(v, "Expected null for missing cell");
        }
    }

    @Test
    void returnsNumberForNumericCell() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("S");
            Row row = sh.createRow(0);
            row.createCell(0).setCellValue(123.45);

            Object v = Utils.getCellValue(row, 0);
            assertTrue(v instanceof Double);
            assertEquals(123.45, (Double) v, 1e-9);
        }
    }

    @Test
    void returnsStringForStringCell() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("S");
            Row row = sh.createRow(0);
            row.createCell(1).setCellValue("abc");

            Object v = Utils.getCellValue(row, 1);
            assertTrue(v instanceof String);
            assertEquals("abc", v);
        }
    }

    @Test
    void returnsBooleanForBooleanCell() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("S");
            Row row = sh.createRow(0);
            row.createCell(2).setCellValue(true);

            Object v = Utils.getCellValue(row, 2);
            assertTrue(v instanceof Boolean);
            assertEquals(true, v);
        }
    }

    @Test
    void returnsEvaluatedValueForNumericFormula() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("S");
            Row row = sh.createRow(0);
            row.createCell(0).setCellValue(2);
            row.createCell(1).setCellValue(3);
            row.createCell(3).setCellFormula("A1+B1"); // index 3

            Object v = Utils.getCellValue(row, 3);
            assertTrue(v instanceof Double);
            assertEquals(5.0, (Double) v, 1e-9);
        }
    }

    @Test
    void returnsEvaluatedValueForStringFormula() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("S");
            Row row = sh.createRow(0);
            row.createCell(4).setCellValue("Hello");
            row.createCell(5).setCellFormula("E1"); // reference string cell

            Object v = Utils.getCellValue(row, 5);
            assertTrue(v instanceof String);
            assertEquals("Hello", v);
        }
    }

    @Test
    void returnsFormulaAndValueForCrossSheetFormula() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheetParams = wb.createSheet("parameters");
            Row rowParams = sheetParams.createRow(1);
            rowParams.createCell(1).setCellValue(42.5);

            Sheet sheetHorizon = wb.createSheet("2025-2026");
            Row rowHorizon = sheetHorizon.createRow(1);
            rowHorizon.createCell(0).setCellValue(10.0);
            var formulaCell = rowHorizon.createCell(3);
            formulaCell.setCellFormula("parameters!B2*A2");

            Utils.FormulaAndValue result = Utils.getFormulaAndValue(formulaCell);
            assertNotNull(result);
            assertTrue(result.hasFormula());
            assertTrue(result.hasValue());
            assertEquals("parameters!B2*A2", result.formula());
            assertEquals(425.0, result.getNumericValue(), 1e-6);
            assertEquals(425.0, (Double) result.value(), 1e-6);
        }
    }

    @Test
    void returnsFormulaAndValueForRegularCell() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("S");
            Row row = sh.createRow(0);
            var cell = row.createCell(0);
            cell.setCellValue(100.0);

            Utils.FormulaAndValue result = Utils.getFormulaAndValue(cell);
            assertNotNull(result);
            assertFalse(result.hasFormula());
            assertTrue(result.hasValue());
            assertNull(result.formula());
            assertEquals(100.0, result.getNumericValue(), 1e-6);
        }
    }

    @Test
    void returnsFormulaAndValueForNullCell() {
        Utils.FormulaAndValue result = Utils.getFormulaAndValue(null);
        assertNotNull(result);
        assertFalse(result.hasFormula());
        assertFalse(result.hasValue());
        assertNull(result.formula());
        assertNull(result.value());
    }
}
