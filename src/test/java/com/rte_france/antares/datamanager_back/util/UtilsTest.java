package com.rte_france.antares.datamanager_back.util;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class UtilsTest {

    @Test
    void getFileChecksum_returnsCorrectChecksum() throws IOException {
        String filePath = "src/test/resources/testFile.xlsx";
        String expectedChecksum = "ddcf3b936326b35bf74caaecb2cb24cfd96f49b6472d1e6bc19c8eccb7a5c51b"; // pre-calculated checksum for "hello" text

        String actualChecksum = Utils.getFileChecksum(filePath);

        assertEquals(expectedChecksum, actualChecksum);
    }

    @Test
    void getFileChecksum_throwsExceptionForNonExistentFile() {
        String filePath = "src/test/resources/nonExistentFile.txt";

        assertThrows(IOException.class, () -> Utils.getFileChecksum(filePath));
    }


    @Test
    void isSameFileWithSameContent_returnsTrueForIdenticalFile() throws IOException {
        File file = new File("src/test/resources/testFile.xlsx");
        TrajectoryEntity trajectoryEntity = new TrajectoryEntity();
        trajectoryEntity.setFileName("testFile");
        trajectoryEntity.setFileSize(file.length());
        trajectoryEntity.setChecksum(Utils.getFileChecksum(file.getPath()));

        boolean isSameFileWithSameContent = Utils.isSameFileWithSameContent(file, trajectoryEntity);

        assertTrue(isSameFileWithSameContent);
    }

    @Test
    void isSameFileWithSameContent_returnsFalseForDifferentFile() throws IOException {
        File file = new File("src/test/resources/testFile.xlsx");
        TrajectoryEntity trajectoryEntity = new TrajectoryEntity();
        trajectoryEntity.setFileName("differentFile");
        trajectoryEntity.setFileSize(file.length());
        trajectoryEntity.setChecksum(Utils.getFileChecksum(file.getPath()));

        boolean isSameFileWithSameContent = Utils.isSameFileWithSameContent(file, trajectoryEntity);

        assertFalse(isSameFileWithSameContent);
    }

    @Test
    void isSameFileWithDifferentContent_returnsTrueForDifferentContent() throws IOException {
        File file = new File("src/test/resources/testFile.xlsx");
        TrajectoryEntity trajectoryEntity = new TrajectoryEntity();
        trajectoryEntity.setFileName("testFile");
        trajectoryEntity.setFileSize(file.length());
        trajectoryEntity.setChecksum("differentChecksum");

        boolean isSameFileWithDifferentContent = Utils.isSameFileWithDifferentContent(file, trajectoryEntity);

        assertTrue(isSameFileWithDifferentContent);
    }

    @Test
    void isSameFileWithDifferentContent_returnsFalseForIdenticalFile() throws IOException {
        File file = new File("src/test/resources/testFile.xlsx");
        TrajectoryEntity trajectoryEntity = new TrajectoryEntity();
        trajectoryEntity.setFileName("testFile");
        trajectoryEntity.setFileSize(file.length());
        trajectoryEntity.setChecksum(Utils.getFileChecksum(file.getPath()));

        boolean isSameFileWithDifferentContent = Utils.isSameFileWithDifferentContent(file, trajectoryEntity);

        assertFalse(isSameFileWithDifferentContent);
    }
}