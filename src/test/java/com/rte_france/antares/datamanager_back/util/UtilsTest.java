package com.rte_france.antares.datamanager_back.util;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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

        var c1 = Utils.computeChecksumByType(f1, TrajectoryType.LINK, horizon);
        var c2 = Utils.computeChecksumByType(f2, TrajectoryType.LINK, horizon);

        assertNotEquals(c1, c2, "Changing parameters for the horizon should change the checksum");
    }

    @Test
    void isSameLoadTrajectory_returnsTrue_whenFilenameAndMtimeMatch() throws Exception {
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

        assertTrue(Utils.isSameLoadTrajectory(file, te));
    }

    @Test
    void isSameLoadTrajectory_returnsFalse_whenMtimeOrFilenameDiffer() throws Exception {
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
        assertFalse(Utils.isSameLoadTrajectory(file, teDifferentMtime));

        var teDifferentName = new com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity();
        teDifferentName.setType(com.rte_france.antares.datamanager_back.dto.TrajectoryType.LOAD.name());
        teDifferentName.setFileName("othertest");
        teDifferentName.setLastModificationContentDate(fileMtime);
        assertFalse(Utils.isSameLoadTrajectory(file, teDifferentName));
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
                () -> Utils.computeChecksumByType(file, TrajectoryType.LINK, horizon)
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


}