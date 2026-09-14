package com.rte_france.antares.datamanager_back.util;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class UtilsAdditionalTest {
    @TempDir
    Path tempDir;

    @Test
    void findHorizonSheet_returnsSheetWhenExists() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("2025-2026");

            Sheet result = Utils.findHorizonSheet(wb, "2025-2026");

            assertThat(result).isSameAs(sheet);
        }
    }

    @Test
    void findHorizonSheet_returnsNullWhenSheetNotExists() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet("2025-2026");

            Sheet result = Utils.findHorizonSheet(wb, "2030-2031");

            assertThat(result).isNull();
        }
    }

    @Test
    void findHorizonSheet_returnsNullWhenHorizonIsNull() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet("2025-2026");

            Sheet result = Utils.findHorizonSheet(wb, null);

            assertThat(result).isNull();
        }
    }

    @Test
    void findHorizonSheet_returnsNullWhenHorizonIsBlank() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet("2025-2026");

            Sheet result = Utils.findHorizonSheet(wb, "   ");

            assertThat(result).isNull();
        }
    }

    @Test
    void findHorizonSheet_returnsNullWhenWorkbookIsNull() {
        Sheet result = Utils.findHorizonSheet(null, "2025-2026");

        assertThat(result).isNull();
    }

    @Test
    void findHorizonSheetOrThrow_returnsSheetWhenExists() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet expectedSheet = wb.createSheet("2025-2026");

            Sheet result = Utils.findHorizonSheetOrThrow(wb, "2025-2026");

            assertThat(result).isSameAs(expectedSheet);
        }
    }

    @Test
    void findHorizonSheetOrThrow_throwsWhenSheetNotExists() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet("2025-2026");

            BusinessException ex = assertThrows(
                    BusinessException.class,
                    () -> Utils.findHorizonSheetOrThrow(wb, "2030-2031")
            );

            assertThat(ex.getMessage()).contains("Missing suitable sheet");
            assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void findHorizonColumnIndex_returnsColumnWhenExists() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("2025");
            header.createCell(1).setCellValue("2030");

            Integer result = Utils.findHorizonColumnIndex(header, "2030");

            assertThat(result).isEqualTo(1);
        }
    }

    @Test
    void findHorizonColumnIndex_returnsNullWhenNotExists() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("2025");

            Integer result = Utils.findHorizonColumnIndex(header, "2030");

            assertThat(result).isNull();
        }
    }

    @Test
    void findHorizonColumnIndex_returnsNullForNumericHeader() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue(2025);
            header.createCell(1).setCellValue(2030);

            Integer result = Utils.findHorizonColumnIndex(header, "2030");

            assertThat(result).isNull();
        }
    }

    @Test
    void startsWithIgnoreCase_returnsTrueWhenPrefixMatches() {
        boolean result = Utils.startsWithIgnoreCase("PREFIX_test", "prefix");

        assertThat(result).isTrue();
    }

    @Test
    void startsWithIgnoreCase_returnsFalseWhenPrefixDoesNotMatch() {
        boolean result = Utils.startsWithIgnoreCase("OTHER_test", "prefix");

        assertThat(result).isFalse();
    }

    @Test
    void startsWithIgnoreCase_returnsFalseWhenStringIsNull() {
        boolean result = Utils.startsWithIgnoreCase(null, "prefix");

        assertThat(result).isFalse();
    }

    @Test
    void startsWithIgnoreCase_returnsTrueWhenStringIsPrefix() {
        boolean result = Utils.startsWithIgnoreCase("prefix", "prefix");

        assertThat(result).isTrue();
    }

    @Test
    void extractAreaFromFileName_returnsAreaWhenFormatIsCorrect() {
        String area = Utils.extractAreaFromFileName("load_at_2030-2031.txt");

        assertThat(area).isEqualTo("at");
    }

    @Test
    void extractAreaFromFileName_returnsNullWhenPrefixIsWrong() {
        String area = Utils.extractAreaFromFileName("notload_at_2030-2031.txt");

        assertThat(area).isNull();
    }

    @Test
    void extractAreaFromFileName_returnsNullWhenTooFewParts() {
        String area = Utils.extractAreaFromFileName("load_at.txt");

        assertThat(area).isNull();
    }

    @Test
    void normalize_returnsUppercaseString() {
        String result = Utils.normalize("  test  ");

        assertThat(result).isEqualTo("TEST");
    }

    @Test
    void normalize_throwsWhenNull() {
        assertThatThrownBy(() -> Utils.normalize(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void getFormulaAndValue_returnsFormulaWhenCellContainsFormula() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);
            Cell cell = row.createCell(0);
            cell.setCellFormula("10+20");

            var result = Utils.getFormulaAndValue(cell);

            assertThat(result.formula()).isEqualTo("10+20");
        }
    }

    @Test
    void getFormulaAndValue_returnsValueWhenCellContainsNumeric() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);
            Cell cell = row.createCell(0);
            cell.setCellValue(42.5);

            var result = Utils.getFormulaAndValue(cell);

            assertThat(result.value()).isEqualTo(42.5);
            assertThat(result.hasFormula()).isFalse();
        }
    }

    @Test
    void getFormulaAndValue_returnsNullWhenCellIsNull() {
        var result = Utils.getFormulaAndValue(null);

        assertThat(result.formula()).isNull();
        assertThat(result.value()).isNull();
    }

    @Test
    void getFormulaAndValue_withRow_returnsValueAtIndex() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);
            row.createCell(2).setCellValue("test value");

            var result = Utils.getFormulaAndValue(row, 2);

            assertThat(result.value()).isEqualTo("test value");
        }
    }

    @Test
    void getCellValue_withRow_returnsValueAtIndex() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);
            row.createCell(1).setCellValue(123);

            Object result = Utils.getCellValue(row, 1);

            assertThat(result).isEqualTo(123.0);
        }
    }

    @Test
    void getCellValue_withNullCell_returnsNull() {
        Object result = Utils.getCellValue(null);

        assertThat(result).isNull();
    }

    @Test
    void getCellValue_withNumericCell_returnsNumericValue() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);
            Cell cell = row.createCell(0);
            cell.setCellValue(99.99);

            Object result = Utils.getCellValue(cell);

            assertThat(result).isEqualTo(99.99);
        }
    }

    @Test
    void getCellValue_withStringCell_returnsStringValue() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);
            Cell cell = row.createCell(0);
            cell.setCellValue("hello");

            Object result = Utils.getCellValue(cell);

            assertThat(result).isEqualTo("hello");
        }
    }

    @Test
    void getCellValue_withBooleanCell_returnsBooleanValue() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);
            Cell cell = row.createCell(0);
            cell.setCellValue(true);

            Object result = Utils.getCellValue(cell);

            assertThat(result).isEqualTo(true);
        }
    }

    @Test
    void toSnakeCase_convertsStringCorrectly() {
        String result = Utils.toSnakeCase("Hello World Test");

        assertThat(result).isEqualTo("hello_world_test");
    }

    @Test
    void toSnakeCase_handlesMultipleSpaces() {
        String result = Utils.toSnakeCase("Hello   World");

        assertThat(result).isEqualTo("hello_world");
    }

    @Test
    void toSnakeCase_handlesLeadingTrailingSpaces() {
        String result = Utils.toSnakeCase("  test string  ");

        assertThat(result).isEqualTo("test_string");
    }

    @Test
    void toSnakeCase_returnsNullWhenNull() {
        String result = Utils.toSnakeCase(null);

        assertThat(result).isNull();
    }

    @Test
    void isNumeric_returnsTrueForNumberType() {
        boolean result = Utils.isNumeric(42.5);

        assertThat(result).isTrue();
    }

    @Test
    void isNumeric_returnsTrueForNumericString() {
        boolean result = Utils.isNumeric("123.45");

        assertThat(result).isTrue();
    }

    @Test
    void isNumeric_returnsFalseForNonNumericString() {
        boolean result = Utils.isNumeric("not a number");

        assertThat(result).isFalse();
    }

    @Test
    void isNumeric_returnsFalseForNull() {
        boolean result = Utils.isNumeric(null);

        assertThat(result).isFalse();
    }

    @Test
    void isNumeric_returnsFalseForNaN() {
        boolean result = Utils.isNumeric(Double.NaN);

        assertThat(result).isFalse();
    }

    @Test
    void isBooleanCell_returnsTrueForBooleanCell() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);
            Cell cell = row.createCell(0);
            cell.setCellValue(true);

            boolean result = Utils.isBooleanCell(cell);

            assertThat(result).isTrue();
        }
    }

    @Test
    void isBooleanCell_returnsTrueForNumeric0() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);
            Cell cell = row.createCell(0);
            cell.setCellValue(0.0);

            boolean result = Utils.isBooleanCell(cell);

            assertThat(result).isTrue();
        }
    }

    @Test
    void isBooleanCell_returnsTrueForNumeric1() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);
            Cell cell = row.createCell(0);
            cell.setCellValue(1.0);

            boolean result = Utils.isBooleanCell(cell);

            assertThat(result).isTrue();
        }
    }

    @Test
    void isBooleanCell_returnsFalseForOtherNumeric() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);
            Cell cell = row.createCell(0);
            cell.setCellValue(42.0);

            boolean result = Utils.isBooleanCell(cell);

            assertThat(result).isFalse();
        }
    }

    @Test
    void isBooleanCell_returnsTrueForStringTrue() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);
            Cell cell = row.createCell(0);
            cell.setCellValue("true");

            boolean result = Utils.isBooleanCell(cell);

            assertThat(result).isTrue();
        }
    }

    @Test
    void isBooleanCell_returnsTrueForStringFalse() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);
            Cell cell = row.createCell(0);
            cell.setCellValue("false");

            boolean result = Utils.isBooleanCell(cell);

            assertThat(result).isTrue();
        }
    }

    @Test
    void isBooleanCell_returnsFalseForNull() {
        boolean result = Utils.isBooleanCell(null);

        assertThat(result).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false", "0", "1", "TRUE", "FALSE"})
    void isBooleanStringValue_returnsTrueForValidBooleanStrings(String value) {
        boolean result = Utils.isBooleanStringValue(value);

        assertThat(result).isTrue();
    }

    @Test
    void isBooleanStringValue_returnsFalseForNonBooleanString() {
        boolean result = Utils.isBooleanStringValue("maybe");

        assertThat(result).isFalse();
    }

    @Test
    void isBooleanStringValue_returnsFalseForNull() {
        boolean result = Utils.isBooleanStringValue(null);

        assertThat(result).isFalse();
    }

    @Test
    void isBooleanStringValue_handlesTrimming() {
        boolean result = Utils.isBooleanStringValue("  true  ");

        assertThat(result).isTrue();
    }

    @Test
    void formatFileName_removesExtension() {
        String result = Utils.formatFileName("my_file.xlsx");

        assertThat(result).isEqualTo("my file");
    }

    @Test
    void formatFileName_replacesDashesWithSpaces() {
        String result = Utils.formatFileName("my-file");

        assertThat(result).isEqualTo("my file");
    }

    @Test
    void formatFileName_replacesUnderscoresWithSpaces() {
        String result = Utils.formatFileName("my_file");

        assertThat(result).isEqualTo("my file");
    }

    @Test
    void formatFileName_replacesMultipleSeparators() {
        String result = Utils.formatFileName("my__--file.txt");

        assertThat(result).isEqualTo("my file");
    }

    @Test
    void formatFileName_returnsNullWhenNull() {
        String result = Utils.formatFileName(null);

        assertThat(result).isNull();
    }

    @Test
    void capitalizeFirstLetter_capitalizesFirstLetter() {
        String result = Utils.capitalizeFirstLetter("hello");

        assertThat(result).isEqualTo("Hello");
    }

    @Test
    void capitalizeFirstLetter_handlesAlreadyCapitalized() {
        String result = Utils.capitalizeFirstLetter("Hello");

        assertThat(result).isEqualTo("Hello");
    }

    @Test
    void capitalizeFirstLetter_handlesEmptyString() {
        String result = Utils.capitalizeFirstLetter("");

        assertThat(result).isEmpty();
    }

    @Test
    void capitalizeFirstLetter_returnsNullWhenNull() {
        String result = Utils.capitalizeFirstLetter(null);

        assertThat(result).isNull();
    }

    @Test
    void capitalizeFirstLetter_handlesBlankString() {
        String result = Utils.capitalizeFirstLetter("   ");

        assertThat(result).isEqualTo("   ");
    }

    @Test
    void getStringCell_returnsStringValue() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue("test");

            String result = Utils.getStringCell(row, 0);

            assertThat(result).isEqualTo("test");
        }
    }

    @Test
    void getStringCell_returnsNullWhenCellIsNull() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);

            String result = Utils.getStringCell(row, 5);

            assertThat(result).isNull();
        }
    }

    @Test
    void getStringCell_convertsNumericToString() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue(42);

            String result = Utils.getStringCell(row, 0);

            assertThat(result).isEqualTo("42.0");
        }
    }

    @Test
    void getStringNumberCell_returnsIntegerForWholeNumber() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue(42.0);

            String result = Utils.getStringNumberCell(row, 0);

            assertThat(result).isEqualTo("42");
        }
    }

    @Test
    void getStringNumberCell_returnsDecimalForDecimal() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue(42.5);

            String result = Utils.getStringNumberCell(row, 0);

            assertThat(result).isEqualTo("42.5");
        }
    }

    @Test
    void getStringNumberCell_returnsNullWhenCellIsEmpty() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);

            String result = Utils.getStringNumberCell(row, 0);

            assertThat(result).isNull();
        }
    }

    @Test
    void validateSelectedAreaPresence_throwsWhenAreaNotInFile() {
        List<String> fileAreas = List.of("AT", "BE", "FR");

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> Utils.validateSelectedAreaPresence("ES", fileAreas, TrajectoryType.DSR, "test.xlsx")
        );

        assertThat(ex.getMessage()).contains("Selected area");
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void validateSelectedAreaPresence_doesNotThrowWhenAreaInFile() {
        List<String> fileAreas = List.of("at", "be", "fr");

        assertThatNoException()
                .isThrownBy(() -> Utils.validateSelectedAreaPresence("AT", fileAreas, TrajectoryType.DSR, "test.xlsx"));
    }

    @Test
    void validateSelectedAreaPresence_doesNotThrowWhenAreaIsBlank() {
        List<String> fileAreas = List.of("AT", "BE");

        assertThatNoException()
                .isThrownBy(() -> Utils.validateSelectedAreaPresence("", fileAreas, TrajectoryType.DSR, "test.xlsx"));
    }

    @Test
    void validateSelectedAreaPresence_doesNotThrowWhenAreaIsOTHERS() {
        List<String> fileAreas = List.of("AT", "BE");

        assertThatNoException()
                .isThrownBy(() -> Utils.validateSelectedAreaPresence("OTHERS", fileAreas, TrajectoryType.DSR, "test.xlsx"));
    }

    @Test
    void validateTrajectoryAreasPresence_throwsWhenNoAreaMatch() {
        List<String> studyAreas = List.of("ES", "PT");
        List<String> fileAreas = List.of("AT", "BE", "FR");

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> Utils.validateTrajectoryAreasPresence(studyAreas, fileAreas, TrajectoryType.DSR, "test.xlsx")
        );

        assertThat(ex.getMessage()).contains("None of the areas");
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void validateTrajectoryAreasPresence_doesNotThrowWhenAreaMatches() {
        List<String> studyAreas = List.of("at", "be");
        List<String> fileAreas = List.of("AT", "BE", "FR");

        assertThatNoException()
                .isThrownBy(() -> Utils.validateTrajectoryAreasPresence(studyAreas, fileAreas, TrajectoryType.DSR, "test.xlsx"));
    }

    @Test
    void validateSelectedOthersAreaPresence_throwsWhenAreaMissing() {
        List<String> areasToCompare = List.of("AT", "ES");
        List<String> fileAreas = List.of("AT", "BE", "FR");

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> Utils.validateSelectedOthersAreaPresence(areasToCompare, fileAreas, TrajectoryType.DSR, "test.xlsx")
        );

        assertThat(ex.getMessage()).contains("Area");
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void validateSelectedOthersAreaPresence_doesNotThrowWhenAllAreasPresent() {
        List<String> areasToCompare = List.of("at", "be");
        List<String> fileAreas = List.of("AT", "BE", "FR");

        assertThatNoException()
                .isThrownBy(() -> Utils.validateSelectedOthersAreaPresence(areasToCompare, fileAreas, TrajectoryType.DSR, "test.xlsx"));
    }

    @Test
    void validateAndResolveTrajectoryPath_returnsResolvedPathWhenValid() throws IOException {
        Path dirPath = tempDir;
        Path filePath = Files.createFile(tempDir.resolve("test.xlsx"));

        Path result = Utils.validateAndResolveTrajectoryPath(dirPath, "test.xlsx");

        assertThat(result).isEqualTo(filePath.toRealPath());
    }

    @Test
    void validateAndResolveTrajectoryPath_throwsWhenPathTraversalAttempted() {
        Path dirPath = tempDir;

        assertThatThrownBy(() -> Utils.validateAndResolveTrajectoryPath(dirPath, "/../../../etc/passwd"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid trajectory path");
    }

    @Test
    void validateEmptyRows_throwsWhenAllRowsEmpty() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> Utils.validateEmptyRows(true, TrajectoryType.DSR, "test.xlsx")
        );

        assertThat(ex.getMessage()).contains("No data found");
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void validateEmptyRows_doesNotThrowWhenDataExists() {
        assertThatNoException()
                .isThrownBy(() -> Utils.validateEmptyRows(false, TrajectoryType.DSR, "test.xlsx"));
    }

    @Test
    void validateDataPresence_throwsWhenOnlyHeader() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> Utils.validateDataPresence(true, "test.xlsx", "2025-2026")
        );

        assertThat(ex.getMessage()).contains("No data in DSR Cluster trajectory");
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void validateDataPresence_doesNotThrowWhenDataPresent() {
        assertThatNoException()
                .isThrownBy(() -> Utils.validateDataPresence(false, "test.xlsx", "2025-2026"));
    }

    @Test
    void validateInvalidCombos_throwsWhenCombosNotEmpty() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> Utils.validateInvalidCombos(
                        java.util.Set.of("combo1", "combo2"),
                        "test.xlsx",
                        TrajectoryType.DSR
                )
        );

        assertThat(ex.getMessage()).contains("not numeric");
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void validateInvalidCombos_doesNotThrowWhenCombosEmpty() {
        assertThatNoException()
                .isThrownBy(() -> Utils.validateInvalidCombos(
                        java.util.Set.of(),
                        "test.xlsx",
                        TrajectoryType.DSR
                ));
    }

    @Test
    void throwTechnicalException_throwsTechnicalException() {
        IOException ioException = new IOException("Test error");

        TechnicalException ex = assertThrows(
                TechnicalException.class,
                () -> Utils.throwTechnicalException(ioException)
        );

        assertThat(ex.getMessage()).contains("Error processing file");
    }

    @Test
    void checkIfHorizonExist_throwsWhenHorizonMissing() throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet("2040-2041");
            try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                wb.write(fos);
            }
        }

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> Utils.checkIfHorizonExist(file, "2025-2026", "DSR")
        );

        assertThat(ex.getMessage()).contains("does not exist");
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void checkIfHorizonExist_doesNotThrowWhenHorizonExists() throws IOException {
        Path file = tempDir.resolve("test.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet("2025-2026");
            try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                wb.write(fos);
            }
        }

        assertThatNoException()
                .isThrownBy(() -> Utils.checkIfHorizonExist(file, "2025-2026", "DSR"));
    }

    @Test
    void civilToChevalHorizon_convertsYearToRange() {
        String result = Utils.civilToChevalHorizon("2030");

        assertThat(result).isEqualTo("2029-2030");
    }

    @Test
    void civilToChevalHorizon_returnsRangeUnchanged() {
        String result = Utils.civilToChevalHorizon("2025-2026");

        assertThat(result).isEqualTo("2025-2026");
    }

    @Test
    void civilToChevalHorizon_returnsNullWhenNull() {
        String result = Utils.civilToChevalHorizon(null);

        assertThat(result).isNull();
    }

    @Test
    void civilToChevalHorizon_handlesLeadingTrailingSpaces() {
        String result = Utils.civilToChevalHorizon("  2030  ");

        assertThat(result).isEqualTo("2029-2030");
    }

    @Test
    void getFormulaAndValue_record_hasFormulaReturnsTrueWhenFormulaPresent() {
        var record = new Utils.FormulaAndValue("=10+20", 30.0);

        assertThat(record.hasFormula()).isTrue();
    }

    @Test
    void getFormulaAndValue_record_hasFormulaReturnsFalseWhenFormulaNull() {
        var record = new Utils.FormulaAndValue(null, 30.0);

        assertThat(record.hasFormula()).isFalse();
    }

    @Test
    void getFormulaAndValue_record_hasValueReturnsTrueWhenValuePresent() {
        var record = new Utils.FormulaAndValue("=10+20", 30.0);

        assertThat(record.hasValue()).isTrue();
    }

    @Test
    void getFormulaAndValue_record_hasValueReturnsFalseWhenValueNull() {
        var record = new Utils.FormulaAndValue("=10+20", null);

        assertThat(record.hasValue()).isFalse();
    }

    @Test
    void getFormulaAndValue_record_getNumericValueReturnsDoubleFromNumber() {
        var record = new Utils.FormulaAndValue(null, 42.5);

        assertThat(record.getNumericValue()).isEqualTo(42.5);
    }

    @Test
    void getFormulaAndValue_record_getNumericValueReturnsDoubleFromString() {
        var record = new Utils.FormulaAndValue(null, "42.5");

        assertThat(record.getNumericValue()).isEqualTo(42.5);
    }

    @Test
    void getFormulaAndValue_record_getNumericValueReturnsNullForInvalidString() {
        var record = new Utils.FormulaAndValue(null, "not a number");

        assertThat(record.getNumericValue()).isNull();
    }

    @Test
    void getFormulaAndValue_record_getStringValueReturnsStringValue() {
        var record = new Utils.FormulaAndValue(null, "test");

        assertThat(record.getStringValue()).isEqualTo("test");
    }

    @Test
    void getFormulaAndValue_record_getStringValueReturnsNullWhenValueNull() {
        var record = new Utils.FormulaAndValue(null, null);

        assertThat(record.getStringValue()).isNull();
    }

    @Test
    void getFormulaAndValue_record_getBooleanValueReturnsTrueFromBoolean() {
        var record = new Utils.FormulaAndValue(null, true);

        assertThat(record.getBooleanValue()).isTrue();
    }

    @Test
    void getFormulaAndValue_record_getBooleanValueReturnsTrueFromString() {
        var record = new Utils.FormulaAndValue(null, "true");

        assertThat(record.getBooleanValue()).isTrue();
    }

    @Test
    void getFormulaAndValue_record_getBooleanValueReturnsFalseFromString() {
        var record = new Utils.FormulaAndValue(null, "false");

        assertThat(record.getBooleanValue()).isFalse();
    }
}












