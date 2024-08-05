package com.rte_france.antares.datamanager_back.util;

import com.rte_france.antares.datamanager_back.exception.ResourceNotFoundException;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;


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
            throw new IOException("could not get file checksum : " + e.getMessage());
        }
    }



    public static boolean isSameFileWithSameContent(File file, TrajectoryEntity trajectoryEntity) throws IOException {
        return getFileNameWithoutExtension(file.getName()).equals(trajectoryEntity.getFileName())
                && trajectoryEntity.getFileSize() == file.length()
                && trajectoryEntity.getChecksum().equals(getFileChecksum(file.getPath()));
    }

    public static boolean isSameFileWithDifferentContent(File file, TrajectoryEntity trajectoryEntity) throws IOException {
        return getFileNameWithoutExtension(file.getName()).equals(trajectoryEntity.getFileName())
                && (trajectoryEntity.getFileSize() != file.length() || !trajectoryEntity.getChecksum().equals(getFileChecksum(file.getPath())));
    }

    /**
     * Builds a trajectory from the given file.
     *
     * @param file the file to process
     * @return the built trajectory
     * @throws IOException if an I/O error occurs
     */
    public static TrajectoryEntity buildTrajectory(File file, TrajectoryEntity existingTrajectory) throws IOException {
        return TrajectoryEntity.builder()
                .fileName(getFileNameWithoutExtension(file.getName()))// file name without extension
                .fileSize(file.length())
                .creationDate(LocalDateTime.now())
                .version(existingTrajectory == null ? 1 : existingTrajectory.getVersion() + 1)
                .checksum(getFileChecksum(file.getPath()))
                .lastModificationContentDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.systemDefault()))
                .build();
    }

    /**
     * Checks the version of a trajectory by comparing the file name, file size, and checksum with a given TrajectoryEntity.
     * If the file has already been processed, a warning is logged and a RuntimeException is thrown.
     *
     * @param file             The file to check the version of.
     * @param trajectoryEntity The TrajectoryEntity to compare the file to.
     * @throws IOException If an I/O error occurs reading from the file or a malformed or unmappable byte sequence is read.
     */
    public static boolean isNewTrajectoryVersionOfSameFile(File file, TrajectoryEntity trajectoryEntity) throws IOException {
        if (isSameFileWithDifferentContent(file, trajectoryEntity)) {
            log.info("File already processed but with different content : " + file.getName());
            return true;
        } else if (isSameFileWithSameContent(file, trajectoryEntity)) {
            log.warn("File already processed : " + file.getName());
            throw new IOException("File already processed : " + file.getName());
        }
        return false;
    }

    public static File getFile(String path, String fileName) {
        File file = new File(path, fileName);
        if (!file.exists()) {
            throw new ResourceNotFoundException("Trajectory not found with file name  : " + fileName);
        }
        return file;
    }

    public static String getFileNameWithoutExtension(String fileName) {
        return fileName.substring(0, fileName.lastIndexOf('.'));
    }
}
