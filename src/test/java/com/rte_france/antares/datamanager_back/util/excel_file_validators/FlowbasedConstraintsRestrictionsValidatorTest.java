package com.rte_france.antares.datamanager_back.util.excel_file_validators;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.FlowbasedConstraintsRestrictionsValidator.ParsedRestrictions;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.FlowbasedConstraintsRestrictionsValidator.RestrictionRow;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.FlowbasedConstraintsRestrictionsValidator.WeightedLink;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.FlowbasedConstraintsRestrictionsColumns;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FlowbasedConstraintsRestrictionsValidatorTest {
    private static final String FILE_NAME = "restriction d'AHC {2027}.xlsx";
    private XSSFWorkbook workbook;
    private Sheet constraints;
    private Sheet lists;

    @BeforeEach
    void setUp() {
        workbook = new XSSFWorkbook();
        constraints = workbook.createSheet("2027");
        Row header = constraints.createRow(0);
        for (var column : FlowbasedConstraintsRestrictionsColumns.values()) {
            header.createCell(column.ordinal()).setCellValue(column.getDisplayName());
        }
        constraint(1, "Restriction", "YES", "listLinks1");
        lists = workbook.createSheet("listLinks");
        lists.createRow(0).createCell(0).setCellValue("listLinks1");
        lists.getRow(0).createCell(1).setCellValue("PoIdS");
        lists.createRow(1).createCell(0).setCellValue("ch-fr");
        lists.getRow(1).createCell(1).setCellValue(-1);
        lists.createRow(2).createCell(0).setCellValue("fr-itn");
        lists.getRow(2).createCell(1).setCellValue(1);
        lists.createRow(3).createCell(0).setCellValue("fr-zz_flowbased");
        lists.getRow(3).createCell(1).setCellValue(0);
    }

    @AfterEach
    void tearDown() throws IOException {
        workbook.close();
    }

    @Test
    void parsesExactContractAndPreservesFieldsWithoutBusinessEnumRules() {
        Row row = constraints.getRow(1);
        row.getCell(0).setCellValue("  Ma Contrainte  ");
        row.createCell(2).setCellValue(" <= ");
        row.createCell(3).setCellValue(" Une Temporalité ");
        row.createCell(4).setCellValue(" arbitrary Type ");
        row.createCell(5).setCellValue("Ignored information");
        row.getCell(6).setCellValue(" listLinks1 ");
        row.createCell(7).setCellValue(" ts10GW ");

        ParsedRestrictions result = parse("2027");

        assertEquals(List.of(new RestrictionRow("Ma Contrainte", true, "<=", "Une Temporalité",
                "arbitrary Type", "listLinks1", "ts10GW")), result.constraints());
        assertEquals(Map.of("listLinks1", List.of(new WeightedLink("ch-fr", -1),
                new WeightedLink("fr-itn", 1), new WeightedLink("fr-zz_flowbased", 0))), result.linkLists());
        assertNotNull(workbook.getSheet("2027"));
    }

    @Test
    void mapsConstraintsAndSharesReferencedLists() {
        constraint(3, "Other", " nO ", "listLinks1");
        constraint(4, "Third", "yEs", "Second LIST");
        lists.getRow(0).createCell(4).setCellValue(" Second LIST ");
        lists.getRow(1).createCell(4).setCellValue(" FR->IT_N ");
        lists.getRow(0).createCell(5).setCellValue("poids");
        lists.getRow(1).createCell(5).setCellValue("-0.25");
        lists.getRow(0).createCell(6).setCellValue("unused");
        lists.getRow(0).createCell(7).setCellValue("poids");
        lists.getRow(1).createCell(6).setCellValue("not imported");
        lists.getRow(1).createCell(7).setCellValue(7);
        lists.getRow(2).getCell(0).setBlank();
        lists.getRow(2).getCell(1).setBlank();
        lists.getRow(3).getCell(0).setCellValue(" ch-fr ");
        lists.getRow(3).getCell(1).setCellValue(2);
        lists.createRow(5).createCell(0).setCellValue("éA/ZZ_+");
        lists.getRow(5).createCell(1).setCellValue(3);
        lists.createRow(4).createCell(0).setCellValue("   ");

        ParsedRestrictions result = parse("2027");

        assertEquals(3, result.constraints().size());
        assertEquals(new RestrictionRow("Other", false, null, null, null, "listLinks1", null),
                result.constraints().get(1));
        assertEquals(List.of("listLinks1", "Second LIST"), List.copyOf(result.linkLists().keySet()));
        assertEquals(List.of(new WeightedLink("ch-fr", -1), new WeightedLink("ch-fr", 2),
                new WeightedLink("éA/ZZ_+", 3)), result.linkLists().get("listLinks1"));
        assertEquals(List.of(new WeightedLink("FR->IT_N", -0.25)), result.linkLists().get("Second LIST"));
    }

    @Test
    void selectsExactHorizonSheetWithoutReadingOtherYearSheets() {
        workbook.createSheet("2026").createRow(0).createCell(0).setCellValue("invalid other year");
        workbook.createSheet("2021");
        Sheet horizonSheet = workbook.createSheet("2026-2027");
        Row header = horizonSheet.createRow(0);
        for (var column : FlowbasedConstraintsRestrictionsColumns.values()) {
            header.createCell(column.ordinal()).setCellValue(column.getDisplayName());
        }
        Row row = horizonSheet.createRow(1);
        row.createCell(0).setCellValue("Range restriction");
        row.createCell(1).setCellValue("YES");
        row.createCell(6).setCellValue("listLinks1");
        assertEquals("Range restriction", parse("2026-2027").constraints().getFirst().name());
    }

    @Test
    void acceptsFixedHeadersCaseInsensitivelyAndTrimsWhitespace() {
        for (Cell cell : constraints.getRow(0)) {
            cell.setCellValue(" " + cell.getStringCellValue().toUpperCase(Locale.ROOT) + " ");
        }
        assertEquals(1, parse("2027").constraints().size());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
    void rejectsEachMissingPhysicalHeader(int column) {
        constraints.getRow(0).removeCell(constraints.getRow(0).getCell(column));
        assertInvalid("2027", "2027", 1, column, "Expected header");
    }

    @ParameterizedTest
    @CsvSource({"0,1", "1,2", "2,3", "3,4", "4,5", "5,6", "6,7"})
    void rejectsMispositionedHeaders(int first, int second) {
        Row header = constraints.getRow(0);
        String value = header.getCell(first).getStringCellValue();
        header.getCell(first).setCellValue(header.getCell(second).getStringCellValue());
        header.getCell(second).setCellValue(value);
        assertInvalid("2027", "2027", 1, first, "Expected header");
    }

    @ParameterizedTest
    @CsvSource({"3,temporalite", "0,name", "1,nom", "6,list_links"})
    void rejectsWrongUnaccentedOrDuplicateFixedHeaders(int column, String value) {
        constraints.getRow(0).getCell(column).setCellValue(value);
        assertInvalid("2027", "2027", 1, column, "Expected header");
    }

    @Test
    void rejectsMissingConstraintHeaderRow() {
        constraints.removeRow(constraints.getRow(0));
        assertInvalid("2027", "2027", 1, 0, "Expected header nom");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 5})
    void rejectsAdditionalPopulatedCellsEvenAfterGaps(int row) {
        Row target = constraints.getRow(row);
        if (target == null) {
            target = constraints.createRow(row);
        }
        target.createCell(12).setCellValue("extra");
        assertInvalid("2027", "2027", row + 1, 12, "Unexpected populated column");
    }

    @Test
    void acceptsBlankExtraCellsAndSkipsOnlyFullyBlankRows() {
        constraints.getRow(0).createCell(10).setCellValue("  ");
        constraints.getRow(1).createCell(12).setBlank();
        constraints.createRow(2);
        constraints.createRow(3).createCell(5).setCellValue("  ");
        constraints.createRow(5).createCell(7).setCellFormula("\"\"");
        assertEquals(1, parse("2027").constraints().size());
    }

    @Test
    void allowsNoConstraintsAndImportsNoLists() {
        constraints.removeRow(constraints.getRow(1));
        ParsedRestrictions result = parse("2027");
        assertTrue(result.constraints().isEmpty());
        assertTrue(result.linkLists().isEmpty());
    }

    @Test
    void rejectsInformationOnlyRowRatherThanSilentlyDroppingIt() {
        constraints.createRow(4).createCell(5).setCellValue("information only");
        assertInvalid("2027", "2027", 5, 0, "Required value is missing: nom");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6})
    void rejectsMissingRequiredConstraintValues(int column) {
        constraints.getRow(1).getCell(column).setCellValue("  ");
        assertInvalid("2027", "2027", 2, column, "Required value is missing");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "true", "false", "1", "0", "y", "oui"})
    void rejectsInvalidEnabled(String value) {
        constraints.getRow(1).getCell(1).setCellValue(value);
        assertInvalid("2027", "2027", 2, 1, "enabled must be YES or NO");
    }

    @Test
    void rejectsNativeBooleanEnabled() {
        constraints.getRow(1).getCell(1).setCellValue(true);
        assertInvalid("2027", "2027", 2, 1, "enabled must be YES or NO");
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "LISTLINKS1", "listlinks1"})
    void resolvesReferencesExactlyIncludingCase(String reference) {
        constraints.getRow(1).getCell(6).setCellValue(reference);
        assertInvalid("2027", "2027", 2, 6, "Referenced list is absent or empty");
    }

    @Test
    void acceptsDistinctCaseSensitiveDynamicListNames() {
        lists.getRow(0).createCell(2).setCellValue("LISTLINKS1");
        lists.getRow(0).createCell(3).setCellValue("poids");
        lists.getRow(1).createCell(2).setCellValue("Second");
        lists.getRow(1).createCell(3).setCellValue(2);
        constraint(2, "Other", "NO", "LISTLINKS1");
        assertEquals(2, parse("2027").linkLists().size());
    }

    @Test
    void rejectsReferencedEmptyList() {
        for (int i = 1; i <= 3; i++) {
            lists.getRow(i).getCell(0).setCellValue(" ");
            lists.getRow(i).getCell(1).setBlank();
        }
        assertInvalid("2027", "2027", 2, 6, "Referenced list is absent or empty");
    }

    @Test
    void rejectsDuplicateListHeadersEvenWhenUnusedAndTrimmed() {
        lists.getRow(0).createCell(2).setCellValue(" unused ");
        lists.getRow(0).createCell(3).setCellValue("Poids");
        lists.getRow(0).createCell(4).setCellValue("unused");
        lists.getRow(0).createCell(5).setCellValue("poids");
        assertInvalid("2027", "listLinks", 1, 4, "Duplicate list header: unused");
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 8})
    void rejectsDataUnderUnnamedColumnsIncludingBeyondLastHeader(int column) {
        lists.getRow(1).createCell(column).setCellValue("link");
        assertInvalid("2027", "listLinks", 2, column, "Data under an unnamed list column");
    }

    @Test
    void rejectsNumericDataUnderUnnamedColumnBeyondLastHeader() {
        lists.getRow(1).createCell(8).setCellValue(0);
        assertInvalid("2027", "listLinks", 2, 8, "Data under an unnamed list column");
    }

    @Test
    void rejectsMissingListHeaderRow() {
        lists.removeRow(lists.getRow(0));
        assertInvalid("2027", "listLinks", 1, 0, "List header row is missing");
    }

    @ParameterizedTest
    @CsvSource({"2027,Missing horizon", "listLinks,Missing required sheet"})
    void rejectsMissingRequiredSheetWithoutFallback(String sheet, String reason) {
        workbook.removeSheetAt(workbook.getSheetIndex(sheet));
        workbook.createSheet("2021");
        BusinessException error = assertThrows(BusinessException.class, () -> parse("2027"));
        assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
        assertEquals(List.of(reason, sheet, "trajectory", FILE_NAME), error.getErrorMessageArguments());
    }

    @Test
    void requiresExactListSheetName() {
        workbook.setSheetName(workbook.getSheetIndex(lists), "LISTLINKS");
        BusinessException error = assertThrows(BusinessException.class, () -> parse("2027"));
        assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
        assertEquals(List.of("Missing required sheet", "listLinks", "trajectory", FILE_NAME),
                error.getErrorMessageArguments());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"27", " 2027 ", "2026-27", "2026-2027-2028", "202A"})
    void rejectsInvalidHorizon(String horizon) {
        assertInvalid(horizon, String.valueOf(horizon), 1, 0, "Expected horizon");
    }

    @Test
    void rejectsMissingWorkbook() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> FlowbasedConstraintsRestrictionsValidator.parse(null, "2027", FILE_NAME, "trajectory"));
        assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
    }

    @Test
    void readsEvaluatedFormulaTextAndPreservesFormattedLinkNames() {
        constraints.getRow(0).getCell(0).setCellFormula("\"nom\"");
        constraints.getRow(1).getCell(1).setCellFormula("\"yEs\"");
        constraints.getRow(1).createCell(7).setCellFormula("10*2");
        Cell number = lists.getRow(1).getCell(0);
        number.setCellValue(7);
        var style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("000"));
        number.setCellStyle(style);
        lists.getRow(2).getCell(0).setCellFormula("\" FR->CH \"");
        lists.getRow(2).getCell(1).setCellFormula("2/3");
        ParsedRestrictions result = parse("2027");
        assertEquals("20", result.constraints().getFirst().secondMember());
        assertEquals(List.of(new WeightedLink("007", -1), new WeightedLink("FR->CH", 2.0 / 3.0),
                new WeightedLink("fr-zz_flowbased", 0)), result.linkLists().get("listLinks1"));
    }

    @Test
    void readsUnderlyingNumericPrecisionAndNumericStringsIndependentlyOfLocale() {
        lists.getRow(1).getCell(1).setCellValue(1.234567891234);
        var rounded = workbook.createCellStyle();
        rounded.setDataFormat(workbook.createDataFormat().getFormat("0.0"));
        lists.getRow(1).getCell(1).setCellStyle(rounded);
        lists.getRow(2).getCell(1).setCellValue("-0.125");
        lists.getRow(3).getCell(1).setCellFormula("1/3");
        lists.getRow(3).getCell(1).setCellStyle(rounded);

        List<WeightedLink> links = parse("2027").linkLists().get("listLinks1");

        assertEquals(1.234567891234, links.get(0).weight(), 0);
        assertEquals(-0.125, links.get(1).weight(), 0);
        assertEquals(1.0 / 3.0, links.get(2).weight(), 0);
    }

    @Test
    void allowsNegativeZeroAndValidatesUnreferencedPairs() {
        lists.getRow(1).getCell(1).setCellValue("-0.0");
        lists.getRow(0).createCell(4).setCellValue("unreferenced");
        lists.getRow(0).createCell(5).setCellValue(" PoIdS ");
        lists.getRow(1).createCell(4).setCellValue("extra-link");
        lists.getRow(1).createCell(5).setCellValue(0.125);

        ParsedRestrictions result = parse("2027");

        assertEquals(Double.doubleToRawLongBits(-0.0),
                Double.doubleToRawLongBits(result.linkLists().get("listLinks1").getFirst().weight()));
        assertEquals(List.of("listLinks1"), List.copyOf(result.linkLists().keySet()));

        lists.getRow(1).getCell(5).setCellValue("not a number");
        assertInvalid("2027", "listLinks", 2, 5, "Weight is not numeric");
    }

    @Test
    void rejectsOldUnweightedFormatAndIncompletePairs() {
        lists.getRow(0).getCell(1).setCellValue("another list");
        assertInvalid("2027", "listLinks", 1, 1, "Expected poids header");

        lists.getRow(0).getCell(1).setCellValue("poids");
        lists.getRow(1).getCell(1).setBlank();
        assertInvalid("2027", "listLinks", 2, 1, "Link has no weight");

        lists.getRow(1).getCell(0).setBlank();
        lists.getRow(1).getCell(1).setCellValue(1);
        assertInvalid("2027", "listLinks", 2, 1, "Weight has no link");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-number", "NaN", "Infinity", "-Infinity"})
    void rejectsNonNumericAndNonFiniteWeights(String weight) {
        lists.getRow(1).getCell(1).setCellValue(weight);
        assertInvalid("2027", "listLinks", 2, 1, "Weight");
    }

    @Test
    void rejectsBooleanAndInvalidFormulaWeights() {
        lists.getRow(1).getCell(1).setCellValue(true);
        assertInvalid("2027", "listLinks", 2, 1, "Weight must be numeric");

        lists.getRow(1).getCell(1).setCellFormula("UNKNOWNFUNCTION(1)");
        assertInvalid("2027", "listLinks", 2, 1, "Cannot evaluate formula");
    }

    @Test
    void rejectsExcelErrorAndFormulaErrorWeightsWithLocation() {
        lists.getRow(1).getCell(1).setCellErrorValue(FormulaError.NA.getCode());
        assertInvalid("2027", "listLinks", 2, 1, "Weight must be numeric");

        lists.getRow(1).getCell(1).setCellFormula("1/0");
        assertInvalid("2027", "listLinks", 2, 1, "Weight must be numeric");
    }

    @Test
    void rejectsOrphanWeightHeader() {
        lists.getRow(0).getCell(0).setCellValue("poids");
        assertInvalid("2027", "listLinks", 1, 0, "Weight header has no list name");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2027", "listLinks"})
    void rejectsExcelErrorCellsWithLocation(String sheet) {
        workbook.getSheet(sheet).getRow(1).getCell(0).setCellErrorValue(FormulaError.NA.getCode());
        assertInvalid("2027", sheet, 2, 0, "Unsupported or error cell type: ERROR");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2027", "listLinks"})
    void rejectsFormulaErrorsWithLocation(String sheet) {
        workbook.getSheet(sheet).getRow(1).getCell(0).setCellFormula("1/0");
        assertInvalid("2027", sheet, 2, 0, "Unsupported or error cell type: ERROR");
    }

    @Test
    void rejectsUnsupportedFormulaWithLocation() {
        constraints.getRow(1).getCell(0).setCellFormula("UNKNOWNFUNCTION(1)");
        assertInvalid("2027", "2027", 2, 0, "Cannot evaluate formula");
    }

    @Test
    void parsedWorkbookReturnsImmutableCollections() {
        ParsedRestrictions result = parse("2027");
        assertThrows(UnsupportedOperationException.class, () -> result.constraints().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.linkLists().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.linkLists().get("listLinks1").clear());
    }

    @Test
    void keepsUserTextOutOfMessageFormatPattern() {
        constraints.getRow(1).getCell(6).setCellValue("list d'été {0}");
        BusinessException error = assertThrows(BusinessException.class, () -> parse("2027"));
        String message = MessageFormat.format(error.getMessage(), error.getErrorMessageArguments().toArray());
        assertTrue(message.contains(FILE_NAME));
        assertTrue(message.contains("list d'été {0}"));
        assertTrue(message.contains("row 2, column G"));
    }

    private void constraint(int rowIndex, String name, String enabled, String list) {
        Row row = constraints.createRow(rowIndex);
        row.createCell(0).setCellValue(name);
        row.createCell(1).setCellValue(enabled);
        row.createCell(6).setCellValue(list);
    }

    private ParsedRestrictions parse(String horizon) {
        return FlowbasedConstraintsRestrictionsValidator.parse(workbook, horizon, FILE_NAME, "trajectory");
    }

    private void assertInvalid(String horizon, String sheet, int row, int column, String reason) {
        BusinessException error = assertThrows(BusinessException.class, () -> parse(horizon));
        assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
        assertEquals(FILE_NAME, error.getErrorMessageArguments().get(0));
        assertEquals(sheet, error.getErrorMessageArguments().get(1));
        assertEquals(Integer.toString(row), error.getErrorMessageArguments().get(2));
        assertEquals(org.apache.poi.ss.util.CellReference.convertNumToColString(column),
                error.getErrorMessageArguments().get(3));
        assertTrue(error.getErrorMessageArguments().get(4).contains(reason),
                () -> "Unexpected error: " + error.getErrorMessageArguments().get(4));
    }
}
