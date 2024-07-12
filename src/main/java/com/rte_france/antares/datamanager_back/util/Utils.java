package com.rte_france.antares.datamanager_back.util;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;


/**
 * Utility class for file and trajectory related operations.
 */
@Slf4j
@UtilityClass
public class Utils {

    /**
     * Calculates and returns the SHA-256 checksum of a file.
     *
     * @param filePath The path of the file to calculate the checksum for.
     * @return The calculated SHA-256 checksum of the file.
     * @throws IOException If an I/O error occurs reading from the file or a malformed or unmappable byte sequence is read.
     */
    public static String getFileChecksum(String filePath) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            return DigestUtils.sha256Hex(fis);
        } catch (IOException e) {
            throw new IOException(e);
        }
    }

    /**
     * Returns the file extension of a file.
     *
     * @param filePath The path of the file to get the extension for.
     * @return The file extension, or an empty string if the file has no extension.
     */
    public static String getFileExtension(Path filePath) {
        String fileName = filePath.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? "" : fileName.substring(dotIndex + 1);
    }



    public static boolean isSameFileWithSameContent(File file, TrajectoryEntity trajectoryEntity) throws IOException {
        return file.getName().equals(trajectoryEntity.getFileName())
                && trajectoryEntity.getFileSize() == file.length()
                && trajectoryEntity.getChecksum().equals(getFileChecksum(file.getPath()));
    }

    public static boolean isSameFileWithDifferentContent(File file, TrajectoryEntity trajectoryEntity) throws IOException {
        return file.getName().equals(trajectoryEntity.getFileName())
                && (trajectoryEntity.getFileSize() != file.length() || !trajectoryEntity.getChecksum().equals(getFileChecksum(file.getPath())));
    }
}
