package com.rte_france.antares.datamanager_back.util;

import com.rte_france.antares.datamanager_back.exception.AlreadyProcessedException;
import com.rte_france.antares.datamanager_back.exception.ResourceNotFoundException;
import com.rte_france.antares.datamanager_back.exception.TechnicalAntaresDataMangerException;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Year;
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
    @ExecutionTime
    public static TrajectoryEntity buildTrajectory(File file, int versionTrajectory, String horizon) throws IOException {
        return TrajectoryEntity.builder()
                .fileName(getFileNameWithoutExtension(file.getName()))// file name without extension
                .fileSize(file.length())
                .creationDate(LocalDateTime.now())
                .version(versionTrajectory == 0 ? 1 : versionTrajectory + 1)
                .checksum(getFileChecksum(file.getPath()))
                .lastModificationContentDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.systemDefault()))
                .horizon(horizon)
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
    public static boolean checkTrajectoryVersion(File file, TrajectoryEntity trajectoryEntity) throws IOException {
        if (isSameFileWithDifferentContent(file, trajectoryEntity)) {
            log.info("File already processed but with different content : " + file.getName());
            return true;
        } else if (isSameFileWithSameContent(file, trajectoryEntity)) {
            throw new AlreadyProcessedException("File already processed : " + file.getName());
        }
        return false;
    }

    public static File getFile(String path, String fileName) {
        File file = new File(path, fileName);
        log.info("File path : " + file.getPath());
        log.info("File name : " + file.getName());
        if (!file.exists()) {
            throw new ResourceNotFoundException("Trajectory not found with file name  : " + fileName);
        }
        return file;
    }

    public static String getFileNameWithoutExtension(String fileName) {
        return fileName.substring(0, fileName.lastIndexOf('.'));
    }

    public static boolean isSheetNameYearNumber(Sheet sheet) {
        String sheetName = sheet.getSheetName();
        try {
            int year = Integer.parseInt(sheetName);
            int currentYear = Year.now().getValue();
            return year >= currentYear;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean eliminateCharacters(String input) {
        return input != null && input.matches("[10]*");
    }


    public Object getCellValue(Row row, int cellIndex) {
        Cell cell = row.getCell(cellIndex);
        if (cell == null) {
            return null;
        }

        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue();
        } else if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }else if (cell.getCellType() == CellType.BOOLEAN) {
            return cell.getBooleanCellValue();
        }

        return null;
    }

    public void checkIfHorizonExist(File file, String horizon) {
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {
            if (workbook.getSheet(horizon) == null)
                throw new TechnicalAntaresDataMangerException("The horizon " + horizon + " does not exist in the file :" + file.getName());
        } catch (IOException e) {
            throw new TechnicalAntaresDataMangerException("could not check if horizon exist : " + e.getMessage());
        }
    }
}