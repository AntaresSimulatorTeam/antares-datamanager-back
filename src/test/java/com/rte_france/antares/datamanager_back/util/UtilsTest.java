package com.rte_france.antares.datamanager_back.util;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.model.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl.RES_CAPACITY_PREFIX;
import static com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl.RES_TECHNOLOGY_DISTRIBUTION_PREFIX;
import static com.rte_france.antares.datamanager_back.util.excel_file_validators.ExcelCommonValidator.checkNumericDataCMorMR;
import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class UtilsTest {
    @TempDir
    Path tempDir;

    @Test
    void getFileChecksum_returnsCorrectChecksum() throws IOException {
        String filePath = "src/test/resources/area/testFile.xlsx";
        String expectedChecksum = "13ed437c4399e34b32b1ba34374179bec3e4e6792048ba0dce5560563107e616"; // pre-calculated checksum for "hello" text

        String actualChecksum = Utils.computeSheetChecksum(filePath, "2030-2031");

        assertEquals(expectedChecksum, actualChecksum);
    }

    @Test
    void getFileChecksum_throwsExceptionForNonExistentFile() {
        String filePath = "src/test/resources/area/nonExistentFile.txt";

        assertThrows(IOException.class, () -> Utils.computeSheetChecksum(filePath, "Sheet1"));
    }


    @Test
    void isSameFileWithSameContent_returnsTrueForIdenticalFile() throws IOException {
        Path path = Path.of("src/test/resources/area/testFile.xlsx");
        TrajectoryEntity trajectoryEntity = new TrajectoryEntity();
        trajectoryEntity.setHorizon("2030-2031");
        trajectoryEntity.setFileName("testFile");
        trajectoryEntity.setFileSize(Files.size(path));
        trajectoryEntity.setType("AREA");
        trajectoryEntity.setChecksum(Utils.computeSheetChecksum(path.toString(), "2030-2031"));

        boolean isSameFileWithSameContent = Utils.isSameFileWithSameContent(path, trajectoryEntity);

        assertTrue(isSameFileWithSameContent);
    }

    @Test
    void isSameFileWithSameContent_returnsFalseForDifferentFile() throws IOException {
        Path path = Path.of("src/test/resources/area/testFile.xlsx");
        TrajectoryEntity trajectoryEntity = new TrajectoryEntity();
        trajectoryEntity.setFileName("differentFile");
        trajectoryEntity.setFileSize(Files.size(path));
        trajectoryEntity.setType("LOAD");
        trajectoryEntity.setChecksum(Utils.computeSheetChecksum(path.toString(), "2030-2031"));

        boolean isSameFileWithSameContent = Utils.isSameFileWithSameContent(path, trajectoryEntity);

        assertFalse(isSameFileWithSameContent);
    }

    @Test
    void isSameFileWithDifferentContent_returnsTrueForDifferentContent() throws IOException {
        Path path = Path.of("src/test/resources/area/testFile.xlsx");
        TrajectoryEntity trajectoryEntity = new TrajectoryEntity();
        trajectoryEntity.setHorizon("2030-2031");
        trajectoryEntity.setFileName("testFile");
        trajectoryEntity.setFileSize(Files.size(path));
        trajectoryEntity.setChecksum("differentChecksum");
        trajectoryEntity.setType("AREA");

        boolean isSameFileWithDifferentContent = Utils.isSameFileWithDifferentContent(path, trajectoryEntity);

        assertTrue(isSameFileWithDifferentContent);
    }

    @Test
    void isSameFileWithDifferentContent_returnsFalseForIdenticalFile() throws IOException {
        Path path = Path.of("src/test/resources/area/testFile.xlsx");
        TrajectoryEntity trajectoryEntity = new TrajectoryEntity();
        trajectoryEntity.setHorizon("2030-2031");
        trajectoryEntity.setFileName("testFile");
        trajectoryEntity.setFileSize(Files.size(path));
        trajectoryEntity.setChecksum(Utils.computeSheetChecksum(path.toString(), "2030-2031"));
        trajectoryEntity.setType("AREA");
        boolean isSameFileWithDifferentContent = Utils.isSameFileWithDifferentContent(path, trajectoryEntity);

        assertFalse(isSameFileWithDifferentContent);
    }

    @Test
    void parseToLocalDateTime_validDate() {
        var validDate = "2023-10-15T10:15:30";
        var expectedDate = LocalDateTime.of(2023, 10, 15, 10, 15, 30);

        var actualDate = Utils.parseToLocalDateTime(validDate);

        assertEquals(expectedDate, actualDate);
    }

    @Test
    void parseToLocalDateTime_invalidDate() {
        var invalidDate = "2023-10-15 10:15:30";

        assertThrows(TechnicalException.class, () -> Utils.parseToLocalDateTime(invalidDate));
    }

    @Test
    void checkTrajectoryVersion_sameContent() throws IOException {
        Path  filePath = Path.of("src/test/resources/area/testFile.xlsx");
        String sheetName = "2030-2031";
        var trajectoryEntity = new TrajectoryEntity();
        trajectoryEntity.setFileName("testFile");
        trajectoryEntity.setFileSize(Files.size(filePath));
        trajectoryEntity.setHorizon(sheetName);
        trajectoryEntity.setChecksum(Utils.computeSheetChecksum(filePath.toString(), "2030-2031"));
        trajectoryEntity.setType("AREA");

        assertThrows(BusinessException.class, () -> Utils.checkTrajectoryVersion(filePath, trajectoryEntity));
    }

    @Test
    void checkTrajectoryVersion_differentContent() throws Exception {
        String filePath = "src/test/resources/area/testFile.xlsx";
        String sheetName = "2030-2031";

        var trajectoryEntity = new TrajectoryEntity();
        trajectoryEntity.setFileName("testFile");
        trajectoryEntity.setFileSize(Files.size(Path.of(filePath)));
        trajectoryEntity.setChecksum("differentChecksum");
        trajectoryEntity.setHorizon(sheetName);
        trajectoryEntity.setType("AREA");

        assertTrue(Utils.checkTrajectoryVersion(Path.of(filePath), trajectoryEntity));
    }

    @Test
    void checkTrajectoryVersion_newFile() throws Exception {
        var tempFile = Files.createFile(tempDir.resolve("testFile.xlsx"));
        Files.writeString(tempFile, "test content");
        var trajectoryEntity = new TrajectoryEntity();
        trajectoryEntity.setFileName("newFile");
        trajectoryEntity.setFileSize(0L);
        trajectoryEntity.setChecksum("newChecksum");
        trajectoryEntity.setType(TrajectoryType.AREA.name());

        assertFalse(Utils.checkTrajectoryVersion(tempFile, trajectoryEntity));
    }

    @Test
    void ensureExtension_addsExtensionIfMissing() {
        var filePath = tempDir.resolve("testFile");
        var result = Utils.ensureExtension(filePath, () -> "txt");

        assertEquals(filePath + ".txt", result.toString());
    }

    @Test
    void ensureExtension_doesNotAddExtensionIfPresent() {
        var filePath = tempDir.resolve("testFile.txt");
        var result = Utils.ensureExtension(filePath, () -> "txt");

        assertEquals(filePath.toString(), result.toString());
    }

    @Test
    void ensureExtension_handlesNullPath() {
        assertThrows(NullPointerException.class, () -> Utils.ensureExtension(null, () -> "txt"));
    }

    @Test
    void ensureExtension_handlesNullSupplier() {
        var filePath = tempDir.resolve("testFile");
        assertThrows(NullPointerException.class, () -> Utils.ensureExtension(filePath, null));
    }

    @Test
    void ensureExtension_handlesEmptyExtension() {
        var filePath = tempDir.resolve("testFile");
        var result = Utils.ensureExtension(filePath, () -> "");

        assertEquals(filePath + ".", result.toString());
    }

    @org.junit.jupiter.api.Test
    void computeSheetChecksum_returnsCorrectChecksumForValidSheet() throws IOException {
        String filePath = "src/test/resources/area/testFile.xlsx";
        String sheetName = "2030-2031";
        String expectedChecksum = "13ed437c4399e34b32b1ba34374179bec3e4e6792048ba0dce5560563107e616";

        String actualChecksum = Utils.computeSheetChecksum(filePath, sheetName);

        assertEquals(expectedChecksum, actualChecksum);
    }

    @Test
    void checkIfHorizonExist_shouldThrowBusinessException_whenHorizonDoesNotExist() throws IOException {
        // Given
        var tempFile = Files.createFile(tempDir.resolve("testFile.xlsx"));
        try (var workbook = new XSSFWorkbook()) {
            workbook.createSheet("2040-2041");
            try (var fos = new FileOutputStream(tempFile.toFile())) {
                workbook.write(fos);
            }
        }

        String horizon = "H1";
        String trajectoryType = TrajectoryType.AREA.name();

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> Utils.checkIfHorizonExist(tempFile, horizon, trajectoryType));

        assertAll(
                () -> assertEquals("Horizon {0} does not exist in the {1} trajectory",
                        exception.getMessage()),
                () -> assertEquals(List.of(horizon, trajectoryType),
                        exception.getErrorMessageArguments()),
                () -> assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus())

        );
    }

    @Test
    void computeLinkChecksum_changesWhenParametersSheetChanges() throws IOException {

        var horizon = "2030-2031";
        TrajectoryEntity trajectoryEntity = TrajectoryEntity.builder().horizon(horizon).type(TrajectoryType.LINK.name()).build();

        var f1 = tempDir.resolve("links_v1.xlsx");
        var f2 = tempDir.resolve("links_v2.xlsx");

        try (var wb = new XSSFWorkbook()) {
            var sh = wb.createSheet(horizon);
            sh.createRow(0).createCell(0).setCellValue("Name");
            sh.getRow(0).createCell(1).setCellValue("Val");
            sh.createRow(1).createCell(0).setCellValue("A-B");
            sh.getRow(1).createCell(1).setCellValue(1);
            var p = wb.createSheet("parameters");
            var hdr = p.createRow(0); hdr.createCell(0).setCellValue("Param"); hdr.createCell(1).setCellValue(horizon);
            var r1 = p.createRow(1); r1.createCell(0).setCellValue("Hurdle Cost"); r1.createCell(1).setCellValue(10);
            try (var fos = new FileOutputStream(f1.toFile())) {
                wb.write(fos);
            }
        }

        // with different hurdle cost
        try (var wb = new XSSFWorkbook()) {
            var sh = wb.createSheet(horizon);
            sh.createRow(0).createCell(0).setCellValue("Name");
            sh.getRow(0).createCell(1).setCellValue("Val");
            sh.createRow(1).createCell(0).setCellValue("A-B");
            sh.getRow(1).createCell(1).setCellValue(1);
            var p = wb.createSheet("parameters");
            var hdr = p.createRow(0); hdr.createCell(0).setCellValue("Param"); hdr.createCell(1).setCellValue(horizon);
            var r1 = p.createRow(1); r1.createCell(0).setCellValue("Hurdle Cost"); r1.createCell(1).setCellValue(20);
            try (var fos = new FileOutputStream(f2.toFile())) {
                wb.write(fos);
            }
        }

        var c1 = Utils.computeChecksumByType(f1, TrajectoryType.LINK, horizon, null);
        var c2 = Utils.computeChecksumByType(f2, TrajectoryType.LINK, horizon, null);

        assertNotEquals(c1, c2, "Changing parameters for the horizon should change the checksum");
    }

    @Test
    void isSameTrajectory_returnsTrue_whenFilenameAndMtimeMatch() throws Exception {
        Path file = tempDir.resolve("test.xlsx");
        Files.writeString(file, "test content");

        var fileMtime = Files.getLastModifiedTime(file)
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .truncatedTo(ChronoUnit.SECONDS);

        var te = new com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity();
        te.setType(com.rte_france.antares.datamanager_back.dto.TrajectoryType.LOAD.name());
        te.setFileName("test");
        te.setLastModificationContentDate(fileMtime);

        assertTrue(Utils.isSameTrajectory(file, te));
    }

    @Test
    void isSameTrajectory_returnsFalse_whenMtimeOrFilenameDiffer() throws Exception {
        Path file = tempDir.resolve("test.xlsx");
        Files.writeString(file, "test content");

        var fileMtime = Files.getLastModifiedTime(file)
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .truncatedTo(ChronoUnit.SECONDS);

        var teDifferentMtime = new com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity();
        teDifferentMtime.setType(com.rte_france.antares.datamanager_back.dto.TrajectoryType.LOAD.name());
        teDifferentMtime.setFileName("test");
        teDifferentMtime.setLastModificationContentDate(fileMtime.minusSeconds(5));
        assertFalse(Utils.isSameTrajectory(file, teDifferentMtime));

        var teDifferentName = new com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity();
        teDifferentName.setType(com.rte_france.antares.datamanager_back.dto.TrajectoryType.LOAD.name());
        teDifferentName.setFileName("othertest");
        teDifferentName.setLastModificationContentDate(fileMtime);
        assertFalse(Utils.isSameTrajectory(file, teDifferentName));
    }

    @Test
    void computeSheetChecksum_throwsTechnicalException_whenSheetMissing() throws Exception {
        Path file = tempDir.resolve("test_missing_sheet.xlsx");
        try (var wb = new XSSFWorkbook()) {
            wb.createSheet("2040-2041");
            try (var fos = new FileOutputStream(file.toFile())) {
                wb.write(fos);
            }
        }

        TechnicalException ex = assertThrows(
                TechnicalException.class,
                () -> Utils.computeSheetChecksum(file.toString(), "2030-2031")
        );

        assertTrue(
                ex.getMessage().startsWith("Feuille '2030-2031' non trouvée"),
                "Unexpected exception message: " + ex.getMessage()
        );
    }

    @Test
    void computeChecksumByType_throwsTechnicalException_whenParametersHeaderMissing_forLink() throws Exception {
        String horizon = "2030-2031";
        Path file = tempDir.resolve("links_no_header.xlsx");

        try (var wb = new XSSFWorkbook()) {
            var sh = wb.createSheet(horizon);
            sh.createRow(0).createCell(0).setCellValue("Name");
            wb.createSheet("parameters");
            try (var fos = new FileOutputStream(file.toFile())) { wb.write(fos); }
        }

        TechnicalException ex = assertThrows(
                TechnicalException.class,
                () -> Utils.computeChecksumByType(file, TrajectoryType.LINK, horizon, null)
        );

        assertTrue(
                ex.getMessage().startsWith("Header row missing in sheet 'parameters'"),
                "Unexpected exception message: " + ex.getMessage()
        );
    }
    @Test
    void getFileNameWithoutExtensionAndWithoutPrefix_areaType_stripsPrefixAndExtension() {
        String name = Utils.getFileNameWithoutExtensionAndWithoutPrefix("areas_BP_2020-2021.xlsx", TrajectoryType.AREA.name());
        assertEquals("BP_2020-2021", name);
    }

    @Test
    void getFileNameWithoutExtensionAndWithoutPrefix_linkType_stripsPrefixAndExtension() {
        String name = Utils.getFileNameWithoutExtensionAndWithoutPrefix("links_scenario.xlsx", TrajectoryType.LINK.name());
        assertEquals("scenario", name);
    }

    @Test
    void getFileNameWithoutExtensionAndWithoutPrefix_thermalCapacityType_stripsPrefixAndExtension() {
        String name = Utils.getFileNameWithoutExtensionAndWithoutPrefix("thermal_capacity.xlsx", TrajectoryType.THERMAL_CAPACITY.name());
        assertEquals("capacity", name);
    }

    @Test
    void getFileNameWithoutExtensionAndWithoutPrefix_thermalCommonParamType_stripsPrefixAndExtensionAndKeepsInnerDots() {
        String name = Utils.getFileNameWithoutExtensionAndWithoutPrefix("common_param_ALF34.xlsx", TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name());
        assertEquals("ALF34", name);
    }

    @Test
    void getFileNameWithoutExtensionAndWithoutPrefix_thermalEconomicCostParamType_stripsPrefixAndExtensionAndKeepsInnerDots() {
        String name = Utils.getFileNameWithoutExtensionAndWithoutPrefix("costs_ALF34.xlsx", TrajectoryType.THERMAL_ECONOMIC_COST_PARAMETER.name());
        assertEquals("ALF34", name);
    }

    @Test
    void getFileNameWithoutExtensionAndWithoutPrefix_thermalDsrClusterType_stripsPrefixAndExtensionAndKeepsInnerDots() {
        String name = Utils.getFileNameWithoutExtensionAndWithoutPrefix("cluster_DSR_ALF34.xlsx", TrajectoryType.DSR.name());
        assertEquals("ALF34", name);
    }

    @Test
    void getFileNameWithoutExtensionAndWithoutPrefix_shouldTechnicalException_fileNameIsBlank() {
        assertThrows(NullPointerException.class, () -> Utils.getFileNameWithoutExtensionAndWithoutPrefix(null, TrajectoryType.DSR.name()));
    }

    @Test void testValidDateFormat() {
        assertTrue(Utils.hasValidDateFormat("2024-01-15T12:30:45"));
    }

    @Test void testInvalidDateFormat() {
        assertFalse(Utils.hasValidDateFormat("2024/01/15 12:30:45"));
    }

    @Test void testInvalidValue() {
        assertFalse(Utils.hasValidDateFormat("not-a-date"));
    }

    @Test
    void testExtractValidStsPath() {
        String msg = "Error: INPUT/ABC12345 for something failed";
        assertEquals("ABC12345", Utils.extractStsPathFromErrorMessage(msg));
    }

    @Test
    void testExtractValidStsPathAtEnd() {
        String msg = "Failure at INPUT/XYZ789";
        assertEquals("XYZ789", Utils.extractStsPathFromErrorMessage(msg));
    }

    @Test
    void testCaseInsensitive() {
        String msg = "warning: input/PathToFile for processing";
        assertEquals("PathToFile", Utils.extractStsPathFromErrorMessage(msg));
    }

    @Test
    void testNoMatchReturnsOriginalMessage() {
        String msg = "No INPUT path found here";
        assertEquals(msg, Utils.extractStsPathFromErrorMessage(msg));
    }

    @Test
    void testNullReturnsNull() {
        assertNull(Utils.extractStsPathFromErrorMessage(null));
    }

    @Test
    void checkParamModulationTrajectoryVersion_shouldReturnFalseWhenNewListIsEmpty() {
        List<ThermalModulationParameterEntity> newList = List.of();
        TrajectoryEntity existingTrajectory = TrajectoryEntity.builder()
                .thermalModulationParameters(List.of(
                        ThermalModulationParameterEntity.builder().tsName("CM_1").checksum("checksum1").build(),
                        ThermalModulationParameterEntity.builder().tsName("MR_1").checksum("checksum2").build()
                ))
                .build();

        boolean result = Utils.checkParamModulationTrajectoryVersion(newList, existingTrajectory);

        assertFalse(result);
    }

    @Test
    void checkParamModulationTrajectoryVersion_shouldReturnFalseWhenExistingListIsEmpty() {
        List<ThermalModulationParameterEntity> newList = List.of(
                ThermalModulationParameterEntity.builder().tsName("CM_1").checksum("checksum1").build(),
                ThermalModulationParameterEntity.builder().tsName("MR_1").checksum("checksum2").build()
        );
        TrajectoryEntity existingTrajectory = TrajectoryEntity.builder()
                .thermalModulationParameters(List.of())
                .build();

        boolean result = Utils.checkParamModulationTrajectoryVersion(newList, existingTrajectory);

        assertFalse(result);
    }

    @Test
    void checkParamModulationTrajectoryVersion_shouldThrowExceptionWhenChecksumsMatch() {
        List<ThermalModulationParameterEntity> newList = List.of(
                ThermalModulationParameterEntity.builder().tsName("CM_1").checksum("checksum1").build(),
                ThermalModulationParameterEntity.builder().tsName("MR_1").checksum("checksum2").build()
        );
        TrajectoryEntity existingTrajectory = TrajectoryEntity.builder().fileName("testFile")
                .thermalModulationParameters(List.of(
                        ThermalModulationParameterEntity.builder().tsName("CM_1").checksum("checksum1").build(),
                        ThermalModulationParameterEntity.builder().tsName("MR_1").checksum("checksum2").build()
                ))
                .build();

        BusinessException exception = assertThrows(BusinessException.class, () ->
                Utils.checkParamModulationTrajectoryVersion(newList, existingTrajectory)
        );

        assertTrue(exception.getMessage().contains("File already processed with same content"));
    }

    @Test
    void shouldPassForNumericValuesWithCommaDecimal() throws IOException {
        Path file = tempDir.resolve("CM_valid_2050.csv");
        String content = """
                DATE_HEURE,heure,ClusterA,ClusterB
                01/01/2028 00:00,1,0,0,320,5
                01/01/2028 01:00,2,0,0,5,1,5
                01/01/2028 02:00,3,0,320684683,0,320750352
                """.replace(",", "."); // optional, if using . decimals instead

        Files.writeString(file, content, StandardCharsets.UTF_8);

        assertDoesNotThrow(() ->
                checkNumericDataCMorMR(file, "T1", "CM")
        );
    }

    @Test
    void shouldThrowWhenNonNumericValueFound() throws IOException {
        Path file = tempDir.resolve("MR_invalid_2050.csv");
        String content = """
                DATE_HEURE,heure,ClusterX,ClusterY
                01/01/2028 00:00,1,abc,123
                01/01/2028 01:00,2,3,4
                """;

        Files.writeString(file, content, StandardCharsets.UTF_8);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> checkNumericDataCMorMR(file, "T2", "MR")
        );

        assertTrue(ex.getMessage().contains("Values for cluster ClusterX are not numeric"));
        assertTrue(ex.getMessage().contains("THERMAL Must Run trajectory T2"));
    }

    @Test
    void shouldIgnoreBlanks() throws IOException {
        Path file = tempDir.resolve("CM_empty_2050.csv");
        String content = """
                DATE_HEURE,heure,ClusterA,ClusterB
                01/01/2028 00:00,1,,123
                01/01/2028 01:00,2,0,0
                """;

        Files.writeString(file, content, StandardCharsets.UTF_8);

        assertDoesNotThrow(() ->

                checkNumericDataCMorMR(file, "T3", "CM")
        );
    }

    @Test
    void calculateThermalCostTrajectoryChecksum_shouldReturnCorrectChecksumForValidInputs() {
        List<ThermalCostTypeEntity> thermalCostsType = List.of(
                ThermalCostTypeEntity.builder()
                        .country("FR")
                        .fuel("Gas")
                        .comment("Comment")
                        .unit("MWh")
                        .modulation("Modulation")
                        .ratioNcvHcv(100d)
                        .thermalCostEntities(List.of(
                                ThermalCostEntity.builder().cost(100d).year(2022).build(),
                                ThermalCostEntity.builder().cost(200d).year(2023).build()
                        ))
                        .build()
        );

        List<ThermalCostsRateEntity> thermalRates = List.of(
                ThermalCostsRateEntity.builder().rateType("Rate1").value(BigDecimal.valueOf(1.5)).year(2022).build(),
                ThermalCostsRateEntity.builder().rateType("Rate2").value(BigDecimal.valueOf(2.5)).year(2023).build()
        );

        String checksum = Utils.calculateThermalCostTrajectoryChecksum(thermalCostsType, thermalRates);

        assertNotNull(checksum);
        assertFalse(checksum.isEmpty());
    }

    @Test
    void calculateThermalCostTrajectoryChecksum_shouldReturnEmptyChecksumForNullInputs() {
        String checksum = Utils.calculateThermalCostTrajectoryChecksum(null, null);

        assertNotNull(checksum);
        assertEquals(Integer.toHexString("".hashCode()), checksum);
    }

    @Test
    void calculateThermalCostTrajectoryChecksum_shouldHandleEmptyListsGracefully() {
        List<ThermalCostTypeEntity> thermalCostsType = List.of();
        List<ThermalCostsRateEntity> thermalRates = List.of();

        String checksum = Utils.calculateThermalCostTrajectoryChecksum(thermalCostsType, thermalRates);

        assertNotNull(checksum);
        assertEquals(Integer.toHexString("".hashCode()), checksum);
    }

    @Test
    void calculateThermalCostTrajectoryChecksum_shouldReturnSameChecksumForIdenticalInputs() {
        List<ThermalCostTypeEntity> thermalCostsType = List.of(
                ThermalCostTypeEntity.builder()
                        .country("FR")
                        .fuel("Gas")
                        .thermalCostEntities(List.of(
                                ThermalCostEntity.builder().cost(100d).year(2022).build()
                        ))
                        .build()
        );

        List<ThermalCostsRateEntity> thermalRates = List.of(
                ThermalCostsRateEntity.builder().rateType("Rate1").value(BigDecimal.valueOf(1.5)).year(2022).build()
        );

        String checksum1 = Utils.calculateThermalCostTrajectoryChecksum(thermalCostsType, thermalRates);
        String checksum2 = Utils.calculateThermalCostTrajectoryChecksum(thermalCostsType, thermalRates);

        assertEquals(checksum1, checksum2);
    }

        @Test
        void testNullCell() {
            assertFalse(Utils.isNumericCell(null));
        }

        @Test
        void testNumericCell() {
            Cell cell = mock(Cell.class);
            when(cell.getCellType()).thenReturn(CellType.NUMERIC);

            assertTrue(Utils.isNumericCell(cell));
        }

        @Test
        void testFormulaNumericResult() {
            Cell cell = mock(Cell.class);
            when(cell.getCellType()).thenReturn(CellType.FORMULA);
            when(cell.getCachedFormulaResultType()).thenReturn(CellType.NUMERIC);

            assertTrue(Utils.isNumericCell(cell));
        }

        @Test
        void testFormulaNonNumericResult() {
            Cell cell = mock(Cell.class);
            when(cell.getCellType()).thenReturn(CellType.FORMULA);
            when(cell.getCachedFormulaResultType()).thenReturn(CellType.STRING);

            assertFalse(Utils.isNumericCell(cell));
        }

        @Test
        void testStringNumeric() {
            Cell cell = mock(Cell.class);
            when(cell.getCellType()).thenReturn(CellType.STRING);
            when(cell.getStringCellValue()).thenReturn(" 123.45 ");

            assertTrue(Utils.isNumericCell(cell));
        }

        @Test
        void testStringEmpty() {
            Cell cell = mock(Cell.class);
            when(cell.getCellType()).thenReturn(CellType.STRING);
            when(cell.getStringCellValue()).thenReturn("   ");

            assertFalse(Utils.isNumericCell(cell));
        }

        @Test
        void testStringNonNumeric() {
            Cell cell = mock(Cell.class);
            when(cell.getCellType()).thenReturn(CellType.STRING);
            when(cell.getStringCellValue()).thenReturn("abc");

            assertFalse(Utils.isNumericCell(cell));
        }

        @Test
        void testOtherType() {
            Cell cell = mock(Cell.class);
            when(cell.getCellType()).thenReturn(CellType.BOOLEAN);

            assertFalse(Utils.isNumericCell(cell));
        }

    @Test
    void testHeaderRowNullThrowsAllMissing() {
        Sheet sheet = mock(Sheet.class);
        when(sheet.getRow(0)).thenReturn(null);

        String[] expected = {"A", "B", "C"};

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> Utils.checkMissingColumns(sheet, expected, "T1", TrajectoryType.STS)
        );
        
        assertTrue(ex.getMessage().contains("Missing columns"));
    }

    @Test
    void testAllColumnsPresent() {
        Sheet sheet = mock(Sheet.class);
        Row row = mock(Row.class);

        when(sheet.getRow(0)).thenReturn(row);
        when(row.getLastCellNum()).thenReturn((short) 3);

        Cell c1 = mock(Cell.class);
        Cell c2 = mock(Cell.class);
        Cell c3 = mock(Cell.class);

        when(row.getCell(0)).thenReturn(c1);
        when(row.getCell(1)).thenReturn(c2);
        when(row.getCell(2)).thenReturn(c3);

        when(c1.toString()).thenReturn("A");
        when(c2.toString()).thenReturn("B");
        when(c3.toString()).thenReturn("C");

        assertDoesNotThrow(() ->
                Utils.checkMissingColumns(sheet, new String[]{"A", "B", "C"}, "T1", TrajectoryType.STS)
        );
    }

    @Test
    void testMissingOneColumn() {
        Sheet sheet = mock(Sheet.class);
        Row row = mock(Row.class);

        when(sheet.getRow(0)).thenReturn(row);
        when(row.getLastCellNum()).thenReturn((short) 2);

        Cell c1 = mock(Cell.class);
        Cell c2 = mock(Cell.class);

        when(row.getCell(0)).thenReturn(c1);
        when(row.getCell(1)).thenReturn(c2);

        when(c1.toString()).thenReturn("A");
        when(c2.toString()).thenReturn("B");

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> Utils.checkMissingColumns(sheet, new String[]{"A", "B", "C"}, "T1", TrajectoryType.DSR)
        );

        assertTrue(ex.getMessage().contains("Missing columns"));
    }

    @Test
    void testCaseInsensitiveMatching() {
        Sheet sheet = mock(Sheet.class);
        Row row = mock(Row.class);

        when(sheet.getRow(0)).thenReturn(row);
        when(row.getLastCellNum()).thenReturn((short) 2);

        Cell c1 = mock(Cell.class);
        Cell c2 = mock(Cell.class);

        when(row.getCell(0)).thenReturn(c1);
        when(row.getCell(1)).thenReturn(c2);

        when(c1.toString()).thenReturn("  a  ");
        when(c2.toString()).thenReturn("B ");

        assertDoesNotThrow(() ->
                Utils.checkMissingColumns(sheet, new String[]{"A", "b"}, "T1", TrajectoryType.STS)
        );
    }

    @Test
    void testEmptyCellsIgnored() {
        Sheet sheet = mock(Sheet.class);
        Row row = mock(Row.class);

        when(sheet.getRow(0)).thenReturn(row);
        when(row.getLastCellNum()).thenReturn((short) 3);

        Cell c1 = mock(Cell.class);
        Cell c2 = mock(Cell.class);
        Cell c3 = mock(Cell.class);

        when(row.getCell(0)).thenReturn(c1);
        when(row.getCell(1)).thenReturn(c2);
        when(row.getCell(2)).thenReturn(c3);

        when(c1.toString()).thenReturn("A");
        when(c2.toString()).thenReturn("   "); // vide → ignoré
        when(c3.toString()).thenReturn("");

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> Utils.checkMissingColumns(sheet, new String[]{"A", "B"}, "T1", TrajectoryType.STS)
        );

        assertTrue(ex.getMessage().contains("Missing columns"));
    }

    @Test
    void testNullCellsIgnored() {
        Sheet sheet = mock(Sheet.class);
        Row row = mock(Row.class);

        when(sheet.getRow(0)).thenReturn(row);
        when(row.getLastCellNum()).thenReturn((short) 2);

        Cell c1 = mock(Cell.class);

        when(row.getCell(0)).thenReturn(c1);
        when(row.getCell(1)).thenReturn(null); // null → ignoré

        when(c1.toString()).thenReturn("A");

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> Utils.checkMissingColumns(sheet, new String[]{"A", "B"}, "T1", TrajectoryType.DSR)
        );

        assertTrue(ex.getMessage().contains("Missing columns"));
    }

    @Test
    void getRequiredSheet_shouldReturnSheet_whenSheetExists() {
        Workbook workbook = mock(Workbook.class);
        Sheet sheet = mock(Sheet.class);
        Path path = Path.of("trajectory.xlsx");

        when(workbook.getNumberOfSheets()).thenReturn(1);
        when(workbook.getSheet("H1")).thenReturn(sheet);

        Sheet result = Utils.getRequiredSheet(workbook, "H1", path.toString(), null);

        assertSame(sheet, result);
    }

    @Test
    void getRequiredSheet_shouldThrowBusinessException_whenSheetDoesNotExist() {
        Workbook workbook = mock(Workbook.class);
        Path path = Path.of("trajectory.xlsx");

        when(workbook.getNumberOfSheets()).thenReturn(1);
        when(workbook.getSheet("H1")).thenReturn(null);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> Utils.getRequiredSheet(workbook, "H1", path.toString(), "DSR")
        );

        assertEquals( List.of("H1", "DSR cluster", "trajectory.xlsx"), ex.getErrorMessageArguments() );
    }

    @Test
    void getRequiredSheet_shouldThrowBusinessException_whenWorkbookIsEmpty() {
        Workbook workbook = mock(Workbook.class);
        Path path = Path.of("trajectory.xlsx");

        when(workbook.getNumberOfSheets()).thenReturn(0);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> Utils.getRequiredSheet(workbook, "H1", path.toString(), "DSR")
        );

        assertEquals( List.of("H1", "DSR cluster", "trajectory.xlsx"), ex.getErrorMessageArguments() );
    }

    @Test
    void getRequiredSheet_shouldIncludeArgumentsInException() {
        Workbook workbook = mock(Workbook.class);
        Path path = Path.of("trajectory.xlsx");

        when(workbook.getNumberOfSheets()).thenReturn(1);
        when(workbook.getSheet("H1")).thenReturn(null);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> Utils.getRequiredSheet(workbook, "H1", path.toString(), "DSR")
        );

        assertEquals(List.of("H1", "DSR cluster", "trajectory.xlsx"), ex.getErrorMessageArguments());
    }

    @Test
    void getRequiredSheet_shouldThrow_whenHorizonIsEmpty() {
        Workbook workbook = mock(Workbook.class);
        Path path = Path.of("trajectory.xlsx");

        when(workbook.getNumberOfSheets()).thenReturn(1);
        when(workbook.getSheet("")).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> Utils.getRequiredSheet(workbook, "", path.toString(), "DSR"));
    }

    @Test
    void getRequiredSheet_shouldUseFileNameInErrorMessage() {
        Workbook workbook = mock(Workbook.class);
        Path path = Path.of("/tmp/trajectory.xlsx");

        when(workbook.getNumberOfSheets()).thenReturn(1);
        when(workbook.getSheet("H1")).thenReturn(null);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> Utils.getRequiredSheet(workbook, "H1", path.getFileName().toString(), TrajectoryType.DSR_CAPACITY_MODULATION.name())
        );

        assertEquals(List.of("H1", "DSR capacity modulation", "trajectory.xlsx"), ex.getErrorMessageArguments());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "solar",
            "  ",
            "SOLAR"
    })
    void shouldNotThrowWhenTechnologyIsPresent(String technologyParam) {
        // Given
        List<String> fileTechnologies = List.of("solar", "wind");

        // When / Then
        assertDoesNotThrow(() ->
                Utils.validateTechnologyPresence(
                        technologyParam,
                        fileTechnologies,
                        TrajectoryType.RES_CAPACITY,
                        "file.xlsx",
                        "AT"
                )
        );
    }

    @Test
    void shouldThrowWhenTechnologyIsMissing() {
        // Given
        String technologyParam = "hydro";
        List<String> fileTechnologies = List.of("solar", "wind");

        // When / Then
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> Utils.validateTechnologyPresence(
                        technologyParam,
                        fileTechnologies,
                        TrajectoryType.RES_CAPACITY,
                        "file.xlsx",
                        "AT"
                )
        );

        assertThat(ex.getMessage())
                .contains("Selected technology")
                .contains("is not present in the 'node' column");
    }

    @Test
    void shouldFindFilesWithPrefixAtDepth1() throws IOException {
        // Given
        Path file1 = Files.createFile(tempDir.resolve("prefix_test.xlsx"));
        Files.createFile(tempDir.resolve("other.xlsx"));

        // When
        List<Path> result = Utils.findFilesFromDepthWithPrefix(tempDir, "prefix", 1, null);

        // Then
        assertThat(result)
                .containsExactly(file1)
                .hasSize(1);
    }

    @Test
    void shouldFindFilesInSubdirectoriesWithinDepth() throws IOException {
        // Given
        Path subDir = Files.createDirectory(tempDir.resolve("sub"));
        Path file1 = Files.createFile(subDir.resolve("prefix_data.xlsx"));

        // When
        List<Path> result = Utils.findFilesFromDepthWithPrefix(tempDir, "prefix", 2, null);

        // Then
        assertThat(result)
                .containsExactly(file1)
                .hasSize(1);
    }

    @Test
    void shouldFindFilesWithTechnologyInSubdirectoriesWithinDepth() throws IOException {
        // Given
        Path subDir = Files.createDirectory(tempDir.resolve("sub"));
        Files.createFile(subDir.resolve("prefix_data.xlsx"));
        Path file2 = Files.createFile(subDir.resolve("prefix_solar.xlsx"));

        // When
        List<Path> result = Utils.findFilesFromDepthWithPrefix(tempDir, "prefix", 2, "solar");

        // Then
        assertThat(result)
                .containsExactly(file2)
                .hasSize(1);
    }

    @Test
    void shouldNotFindFilesDeeperThanAllowedDepth() throws IOException {
        // Given
        Path subDir = Files.createDirectory(tempDir.resolve("sub"));
        Path deepDir = Files.createDirectory(subDir.resolve("deep"));
        Files.createFile(deepDir.resolve("prefix_hidden.xlsx"));

        // When & Then : Vérifie que IOException est levée si aucun fichier trouvé
        assertThatThrownBy(() -> Utils.findFilesFromDepthWithPrefix(tempDir, "prefix", 1, null))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("No files found matching criteria in directory: " + tempDir);
    }

    @Test
    void shouldIgnoreFilesWithWrongExtension() throws IOException {
        // Given
        Files.createFile(tempDir.resolve("prefix_test.txt"));
        Files.createFile(tempDir.resolve("prefix_test.csv"));

        // When & Then : Vérifie que IOException est levée si aucun fichier trouvé
        assertThatThrownBy(() -> Utils.findFilesFromDepthWithPrefix(tempDir, "prefix", 1, null))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("No files found matching criteria in directory: " + tempDir);
    }

    @Test
    void shouldIgnoreFilesWithWrongPrefix() throws IOException {
        // Given
        Files.createFile(tempDir.resolve("wrongprefix.xlsx"));

        // When & Then : Vérifie que IOException est levée si aucun fichier trouvé
        assertThatThrownBy(() -> Utils.findFilesFromDepthWithPrefix(tempDir, "prefix", 1, null))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("No files found matching criteria in directory: " + tempDir);
    }

    @Test
    void shouldBeCaseInsensitiveOnPrefix() throws IOException {
        // Given
        Path file = Files.createFile(tempDir.resolve("Prefix_Upper.xlsx"));

        // When
        List<Path> result = Utils.findFilesFromDepthWithPrefix(tempDir, "prefix", 1, null);

        // Then
        assertThat(result)
                .containsExactly(file)
                .hasSize(1);
    }

        @Test
        void shouldReturn1WhenOnlyFirstCellIsFilled() {
            Workbook wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);

            row.createCell(0).setCellValue("A");

            int result = Utils.getRealLastColumn(row);

            assertThat(result).isEqualTo(1);
        }

        @Test
        void shouldReturn3WhenLastNonBlankCellIsAtIndex2() {
            Workbook wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);

            row.createCell(0).setCellValue("A");
            row.createCell(1).setBlank();
            row.createCell(2).setCellValue("C");

            int result = Utils.getRealLastColumn(row);

            assertThat(result).isEqualTo(3);
        }

        @Test
        void shouldIgnoreTrailingBlankCells() {
            Workbook wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);

            row.createCell(0).setCellValue("A");
            row.createCell(1).setCellValue("B");
            row.createCell(2).setBlank();
            row.createCell(3).setBlank();

            int result = Utils.getRealLastColumn(row);

            assertThat(result).isEqualTo(2);
        }

        @Test
        void shouldReturn0WhenAllCellsAreBlank() {
            Workbook wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);

            row.createCell(0).setBlank();
            row.createCell(1).setBlank();

            int result = Utils.getRealLastColumn(row);

            assertThat(result).isZero();
        }

        @Test
        void shouldReturn0WhenRowHasNoCells() {
            Workbook wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);

            int result = Utils.getRealLastColumn(row);

            assertThat(result).isZero();
        }

        @Test
        void shouldHandleMixedTypesCorrectly() {
            Workbook wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);

            row.createCell(0).setCellValue(42);
            row.createCell(1).setCellValue(true);
            row.createCell(2).setBlank();
            row.createCell(3).setCellValue("text");

            int result = Utils.getRealLastColumn(row);

            assertThat(result).isEqualTo(4);
        }

        @Test
        void shouldReturnColumnIndexWhenStringMatches() {
            Workbook wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);

            header.createCell(5).setCellValue("2025");
            header.createCell(6).setCellValue("2030");

            int result = Utils.getYearColIndex(5,7, header, "2030", -1);

            assertThat(result).isEqualTo(6);
        }

        @Test
        void shouldReturnColumnIndexWhenNumericMatches() {
            Workbook wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);

            header.createCell(5).setCellValue(2025);
            header.createCell(6).setCellValue(2030);

            int result = Utils.getYearColIndex(5,7, header, "2025", -1);

            assertThat(result).isEqualTo(5);
        }

        @Test
        void shouldReturnColumnIndexWhenFormulaEvaluatesToNumeric() {
            Workbook wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);

            Cell cell = header.createCell(5);
            cell.setCellFormula("2000+25"); // = 2025

            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            evaluator.evaluateFormulaCell(cell);

            int result = Utils.getYearColIndex(5,6, header, "2025", -1);

            assertThat(result).isEqualTo(5);
        }

        @Test
        void shouldReturnDefaultValueWhenNoMatchFound() {
            Workbook wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);

            header.createCell(5).setCellValue("2025");
            header.createCell(6).setCellValue("2030");

            int result = Utils.getYearColIndex(5,7, header, "2040", -1);

            assertThat(result).isEqualTo(-1);
        }

        @Test
        void shouldSkipBlankCells() {
            Workbook wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);

            header.createCell(5).setBlank();
            header.createCell(6).setCellValue("2030");

            int result = Utils.getYearColIndex(5,7, header, "2030", -1);

            assertThat(result).isEqualTo(6);
        }

        @Test
        void shouldHandleFormulaReturningString() {
            Workbook wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);

            Cell cell = header.createCell(5);
            cell.setCellFormula("\"2025\""); // formule renvoyant une STRING

            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            evaluator.evaluateFormulaCell(cell);

            int result = Utils.getYearColIndex(5,6, header, "2025", -1);

            assertThat(result).isEqualTo(5);
        }

        @Test
        void validatePrefixIfNeeded_shouldNotThrowWhenAreaIsFR() {
            // Given
            String areaParam = "FR";
            String trajectory = "whatever.xlsx";

            // When / Then
            assertThatNoException()
                    .isThrownBy(() -> Utils.validatePrefixIfNeeded(areaParam, trajectory, TrajectoryType.RES_CAPACITY, RES_CAPACITY_PREFIX));
        }

        @Test
        void validatePrefixIfNeeded_shouldThrowWhenPrefixDoesNotMatch() {
            // Given
            String areaParam = "ES";
            String trajectory = "wrongprefix_2025.xlsx";

            // When
            BusinessException ex = catchThrowableOfType(
                    () -> Utils.validatePrefixIfNeeded(areaParam, trajectory, TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION, RES_TECHNOLOGY_DISTRIBUTION_PREFIX),
                    BusinessException.class
            );

            // Then
            assertThat(ex).isNotNull();
            assertThat(ex.getMessage())
                    .contains("must start with");
        }

        @Test
        void validatePrefixIfNeeded_shouldThrowWhenAreaIsNotFRAndPrefixMissing() {
            // Given
            String areaParam = "BE";
            String trajectory = "capacity_2030.xlsx";

            // When
            BusinessException ex = catchThrowableOfType(
                    () -> Utils.validatePrefixIfNeeded(areaParam, trajectory, TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION, RES_TECHNOLOGY_DISTRIBUTION_PREFIX),
                    BusinessException.class
            );

            // Then
            assertThat(ex).isNotNull();
            assertThat(ex.getErrorMessageArguments())
                    .containsExactly(RES_TECHNOLOGY_DISTRIBUTION_PREFIX);
        }



    @Test
    void getFirstSheetOrThrow_shouldReturnFirstSheetWhenWorkbookHasSheets() {
        // Given
        Workbook wb = new XSSFWorkbook();
        Sheet expectedSheet = wb.createSheet("FirstSheet");
        Path filePath = Path.of("test.xlsx");

        // When
        Sheet result = Utils.getSheetOrThrow(wb, filePath, 0);

        // Then
        assertThat(result).isSameAs(expectedSheet);
    }

    @Test
    void getFirstSheetOrThrow_shouldThrowWhenWorkbookHasNoSheets() {
        // Given
        Workbook wb = new XSSFWorkbook(); // no sheet created
        Path filePath = Path.of("empty.xlsx");

        // When
        BusinessException ex = catchThrowableOfType(
                () -> Utils.getSheetOrThrow(wb, filePath, 0),
                BusinessException.class
        );

        // Then
        assertThat(ex).isNotNull();
        assertThat(ex.getMessage())
                .contains("InstalledRes file has no sheet")
                .contains("empty.xlsx");
        assertThat(ex.getHttpStatus().value()).isEqualTo(400);
    }

    @Test
    void getFirstSheetOrThrow_shouldIncludeFileNameInErrorMessage() {
        // Given
        Workbook wb = new XSSFWorkbook();
        Path filePath = Path.of("myTrajectory.xlsx");

        // When
        BusinessException ex = catchThrowableOfType(
                () -> Utils.getSheetOrThrow(wb, filePath, 0),
                BusinessException.class
        );

        // Then
        assertThat(ex.getMessage()).contains("myTrajectory.xlsx");
    }

        @Test
        void shouldReturnHeaderWhenRow0Exists() {
            // Given
            XSSFWorkbook wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet();
            Row expectedHeader = sheet.createRow(0);
            Path filePath = Path.of("test.xlsx");

            // When
            Row result = Utils.getHeaderOrThrow(sheet, filePath, TrajectoryType.RES_CAPACITY);

            // Then
            assertThat(result).isSameAs(expectedHeader);
        }

        @Test
        void shouldThrowWhenHeaderIsMissing() {
            // Given
            XSSFWorkbook wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet(); // no row created
            Path filePath = Path.of("missingHeader.xlsx");

            // When
            BusinessException ex = catchThrowableOfType(
                    () -> Utils.getHeaderOrThrow(sheet, filePath, TrajectoryType.RES_CAPACITY),
                    BusinessException.class
            );

            // Then
            assertThat(ex).isNotNull();
            assertThat(ex.getMessage())
                    .contains("Missing header");
            assertThat(ex.getHttpStatus().value()).isEqualTo(400);
        }

        @Test
        void shouldIncludeFileNameInErrorMessage() {
            // Given
            XSSFWorkbook wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet();
            Path filePath = Path.of("headerFile.xlsx");

            // When
            BusinessException ex = catchThrowableOfType(
                    () -> Utils.getHeaderOrThrow(sheet, filePath, TrajectoryType.RES_CAPACITY),
                    BusinessException.class
            );

            // Then
            assertThat(ex).isNotNull();
            assertThat(ex.getErrorMessageArguments())
                    .contains("RES Installed power", filePath.getFileName().toString());
            assertThat(ex.getMessage()).isEqualTo("Missing header in {0} file: {1}");
        }

    @Test
    void shouldNotThrowWhenHeaderIsValidAndColumnsPresent() {
        try (MockedStatic<Utils> utilities = Mockito.mockStatic(Utils.class, Mockito.CALLS_REAL_METHODS)) {

            // Given
            Workbook wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);

            // simulate 6 columns minimum
            header.createCell(0).setCellValue("A");
            header.createCell(1).setCellValue("B");
            header.createCell(2).setCellValue("C");
            header.createCell(3).setCellValue("D");
            header.createCell(4).setCellValue("E");
            header.createCell(5).setCellValue("F");

            String[] required = {"A", "B"};

            // Mock checkMissingColumns to do nothing
            utilities.when(() -> Utils.checkMissingColumns(
                    Mockito.eq(sheet),
                    Mockito.eq(required),
                    Mockito.anyString(),
                    Mockito.any()
            )).thenAnswer(inv -> null);

            // When / Then
            assertThatNoException()
                    .isThrownBy(() -> Utils.validateHeaderColumns(header, sheet, required, "trajectory", TrajectoryType.DSR));
        }
    }

    @Test
    void shouldThrowWhenHeaderHasLessThanSixColumns() {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet();
        Row header = sheet.createRow(0);

        // Only 3 columns
        header.createCell(0).setCellValue("A");
        header.createCell(1).setCellValue("B");
        header.createCell(2).setCellValue("C");

        String[] required = {"A", "B", "C", "D", "E"};

        BusinessException ex = catchThrowableOfType(
                () -> Utils.validateHeaderColumns(header, sheet, required, "trajectory", TrajectoryType.DSR),
                BusinessException.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getMessage()).contains("{0} trajectory header is invalid");
        assertThat(ex.getHttpStatus().value()).isEqualTo(400);
    }

    @Test
    void shouldThrowWhenRequiredColumnsAreMissing() {
        try (MockedStatic<Utils> utilities = Mockito.mockStatic(Utils.class, Mockito.CALLS_REAL_METHODS)) {

            // Given
            Workbook wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);

            // 6 colonnes valides
            for (int i = 0; i < 6; i++) {
                header.createCell(i).setCellValue("X");
            }

            String[] required = {"col1", "col2"};

            // Mock checkMissingColumns to throw
            utilities.when(() -> Utils.checkMissingColumns(
                    Mockito.eq(sheet),
                    Mockito.eq(required),
                    Mockito.anyString(),
                    Mockito.any()
            )).thenThrow(
                    BusinessException.builder()
                            .message("Missing columns")
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build()
            );

            // When
            BusinessException ex = catchThrowableOfType(
                    () -> Utils.validateHeaderColumns(header, sheet, required, "trajectory", TrajectoryType.STS),
                    BusinessException.class
            );

            // Then
            assertThat(ex).isNotNull();
            assertThat(ex.getMessage()).contains("Missing columns");
        }
    }

    @Test
    void shouldReturnYearColumnIndexWhenFound() {
        try (MockedStatic<Utils> utilities = Mockito.mockStatic(Utils.class, Mockito.CALLS_REAL_METHODS)) {

            // Given
            XSSFWorkbook wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);

            // Mock getRealLastColumn
            utilities.when(() -> Utils.getRealLastColumn(header))
                    .thenReturn(10);

            // Mock getYearColIndex
            utilities.when(() -> Utils.getYearColIndex(5,10, header, "2030", -1))
                    .thenReturn(7);

            // When
            int result = Utils.resolveYearColumnIndex(header, "2025-2030", TrajectoryType.DSR, "trajectory", 5,false);

            // Then
            assertThat(result).isEqualTo(7);
        }
    }

    @Test
    void shouldThrowWhenYearColumnNotFound() {
        try (MockedStatic<Utils> utilities = Mockito.mockStatic(Utils.class, Mockito.CALLS_REAL_METHODS)) {

            // Given
            XSSFWorkbook wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);

            utilities.when(() -> Utils.getRealLastColumn(header))
                    .thenReturn(10);

            utilities.when(() -> Utils.getYearColIndex(5,10, header, "2030", -1))
                    .thenReturn(-1);

            // When
            BusinessException ex = catchThrowableOfType(
                    () -> Utils.resolveYearColumnIndex(header, "2025-2030", TrajectoryType.DSR, "trajectory",5,false),
                    BusinessException.class
            );

            // Then
            assertThat(ex).isNotNull();
            assertThat(ex.getMessage())
                    .contains("Horizon {0} does not exist");
            assertThat(ex.getHttpStatus().value()).isEqualTo(400);
        }
    }

    @Test
    void shouldExtractHorizonYearCorrectly() {
        try (MockedStatic<Utils> utilities = Mockito.mockStatic(Utils.class, Mockito.CALLS_REAL_METHODS)) {

            // Given
            XSSFWorkbook wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);

            utilities.when(() -> Utils.getRealLastColumn(header))
                    .thenReturn(10);

            // We check that the method extracts "2035" from "2020-2035"
            utilities.when(() -> Utils.getYearColIndex(5,10, header, "2035", -1))
                    .thenReturn(4);

            // When
            int result = Utils.resolveYearColumnIndex(header, "2020-2035", TrajectoryType.DSR, "trajectory", 5,false);

            // Then
            assertThat(result).isEqualTo(4);
        }
    }

    @Test
    void calculateDirectoryChecksum_returnsSameChecksumForSameContentRegardlessOfCreationOrder(@TempDir Path testDir) throws IOException {
        Path dirA = testDir.resolve("a");
        Path dirB = testDir.resolve("b");
        Files.createDirectories(dirA);
        Files.createDirectories(dirB);

        Files.writeString(dirA.resolve("z.csv"), "alpha");
        Files.writeString(dirA.resolve("a.csv"), "beta");

        Files.writeString(dirB.resolve("a.csv"), "beta");
        Files.writeString(dirB.resolve("z.csv"), "alpha");

        String checksumA = Utils.calculateDirectoryChecksum(dirA);
        String checksumB = Utils.calculateDirectoryChecksum(dirB);

        assertEquals(checksumA, checksumB);
    }

    @Test
    void calculateDirectoryChecksum_throwsTechnicalExceptionWhenDirectoryIsNull() {
        assertThatThrownBy(() -> Utils.calculateDirectoryChecksum(null))
                .isInstanceOf(TechnicalException.class)
                .hasMessageContaining("directory path is null");
    }

    @Test
    void calculateDirectoryChecksum_throwsTechnicalExceptionWhenPathIsNotDirectory(@TempDir Path testDir) throws IOException {
        Path filePath = testDir.resolve("not_a_directory.txt");
        Files.writeString(filePath, "content");

        assertThatThrownBy(() -> Utils.calculateDirectoryChecksum(filePath))
                .isInstanceOf(TechnicalException.class)
                .hasMessageContaining("directory does not exist or is not a directory");
    }

    @Test
    void calculateDirectoryChecksum_throwsTechnicalExceptionWhenFileResolvesOutsideBaseDirectory(@TempDir Path testDir) throws IOException {
        Path baseDir = testDir.resolve("base");
        Path outsideFile = testDir.resolve("outside.txt");
        Files.createDirectories(baseDir);
        Files.writeString(outsideFile, "outside");

        Path linkPath = baseDir.resolve("escape.txt");
        try {
            Files.createSymbolicLink(linkPath, outsideFile);
        } catch (UnsupportedOperationException | SecurityException e) {
            return;
        }

        assertThatThrownBy(() -> Utils.calculateDirectoryChecksum(baseDir))
                .isInstanceOf(TechnicalException.class)
                .hasMessageContaining("outside of the allowed directory");
    }
}