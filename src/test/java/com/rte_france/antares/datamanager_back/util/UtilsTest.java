package com.rte_france.antares.datamanager_back.util;

import com.rte_france.antares.datamanager_back.exception.AlreadyProcessedException;
import com.rte_france.antares.datamanager_back.exception.TechnicalAntaresDataMangerException;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class UtilsTest {
    @TempDir
    Path tempDir;

    @Test
    void getFileChecksum_returnsCorrectChecksum() throws IOException {
        String filePath = "src/test/resources/area/testFile.xlsx";
        String expectedChecksum = "ddcf3b936326b35bf74caaecb2cb24cfd96f49b6472d1e6bc19c8eccb7a5c51b"; // pre-calculated checksum for "hello" text

        String actualChecksum = Utils.getFileChecksum(filePath);

        assertEquals(expectedChecksum, actualChecksum);
    }

    @Test
    void getFileChecksum_throwsExceptionForNonExistentFile() {
        String filePath = "src/test/resources/area/nonExistentFile.txt";

        assertThrows(IOException.class, () -> Utils.getFileChecksum(filePath));
    }


    @Test
    void isSameFileWithSameContent_returnsTrueForIdenticalFile() throws IOException {
        Path path = Path.of("src/test/resources/area/testFile.xlsx");
        TrajectoryEntity trajectoryEntity = new TrajectoryEntity();
        trajectoryEntity.setFileName("testFile");
        trajectoryEntity.setFileSize(Files.size(path));
        trajectoryEntity.setChecksum(Utils.getFileChecksum(path.toString()));

        boolean isSameFileWithSameContent = Utils.isSameFileWithSameContent(path, trajectoryEntity);

        assertTrue(isSameFileWithSameContent);
    }

    @Test
    void isSameFileWithSameContent_returnsFalseForDifferentFile() throws IOException {
        Path path = Path.of("src/test/resources/area/testFile.xlsx");
        TrajectoryEntity trajectoryEntity = new TrajectoryEntity();
        trajectoryEntity.setFileName("differentFile");
        trajectoryEntity.setFileSize(Files.size(path));
        trajectoryEntity.setChecksum(Utils.getFileChecksum(path.toString()));

        boolean isSameFileWithSameContent = Utils.isSameFileWithSameContent(path, trajectoryEntity);

        assertFalse(isSameFileWithSameContent);
    }

    @Test
    void isSameFileWithDifferentContent_returnsTrueForDifferentContent() throws IOException {
        Path path = Path.of("src/test/resources/area/testFile.xlsx");
        TrajectoryEntity trajectoryEntity = new TrajectoryEntity();
        trajectoryEntity.setFileName("testFile");
        trajectoryEntity.setFileSize(Files.size(path));
        trajectoryEntity.setChecksum("differentChecksum");

        boolean isSameFileWithDifferentContent = Utils.isSameFileWithDifferentContent(path, trajectoryEntity);

        assertTrue(isSameFileWithDifferentContent);
    }

    @Test
    void isSameFileWithDifferentContent_returnsFalseForIdenticalFile() throws IOException {
        Path path = Path.of("src/test/resources/area/testFile.xlsx");
        TrajectoryEntity trajectoryEntity = new TrajectoryEntity();
        trajectoryEntity.setFileName("testFile");
        trajectoryEntity.setFileSize(Files.size(path));
        trajectoryEntity.setChecksum(Utils.getFileChecksum(path.toString()));

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

        assertThrows(TechnicalAntaresDataMangerException.class, () -> Utils.parseToLocalDateTime(invalidDate));
    }

    @Test
    void checkTrajectoryVersion_sameContent() throws IOException {
        var tempFile = Files.createFile(tempDir.resolve("testFile.xlsx"));
        Files.writeString(tempFile, "test content");
        var trajectoryEntity = new TrajectoryEntity();
        trajectoryEntity.setFileName("testFile");
        trajectoryEntity.setFileSize(Files.size(tempFile));
        trajectoryEntity.setChecksum(Utils.getFileChecksum(tempFile.toString()));

        assertThrows(AlreadyProcessedException.class, () -> Utils.checkTrajectoryVersion(tempFile, trajectoryEntity));
    }

    @Test
    void checkTrajectoryVersion_differentContent() throws IOException {
        var tempFile = Files.createFile(tempDir.resolve("testFile.xlsx"));
        Files.writeString(tempFile, "test content");
        var trajectoryEntity = new TrajectoryEntity();
        trajectoryEntity.setFileName("testFile");
        trajectoryEntity.setFileSize(Files.size(tempFile));
        trajectoryEntity.setChecksum("differentChecksum");

        assertTrue(Utils.checkTrajectoryVersion(tempFile, trajectoryEntity));
    }

    @Test
    void checkTrajectoryVersion_newFile() throws IOException {
        var tempFile = Files.createFile(tempDir.resolve("testFile.xlsx"));
        Files.writeString(tempFile, "test content");
        var trajectoryEntity = new TrajectoryEntity();
        trajectoryEntity.setFileName("newFile");
        trajectoryEntity.setFileSize(0L);
        trajectoryEntity.setChecksum("newChecksum");

        assertFalse(Utils.checkTrajectoryVersion(tempFile, trajectoryEntity));
    }
}