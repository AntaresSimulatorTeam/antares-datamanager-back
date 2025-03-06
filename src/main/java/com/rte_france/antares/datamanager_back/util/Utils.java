package com.rte_france.antares.datamanager_back.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rte_france.antares.datamanager_back.exception.AlreadyProcessedException;
import com.rte_france.antares.datamanager_back.exception.TechnicalAntaresDataMangerException;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.poi.ss.usermodel.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;


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
        try (InputStream inputStream = Files.newInputStream(Path.of(filePath))) {
            return DigestUtils.sha256Hex(inputStream);
        } catch (IOException e) {
            throw new IOException("could not get file checksum : " + e.getMessage());
        }
    }


    public static boolean isSameFileWithSameContent(Path path, TrajectoryEntity trajectoryEntity) throws IOException {
        return getFileNameWithoutExtension(path.getFileName().toString()).equals(trajectoryEntity.getFileName())
                && trajectoryEntity.getFileSize() == Files.size(path)
                && trajectoryEntity.getChecksum().equals(getFileChecksum(path.toString()));
    }

    public static boolean isSameFileWithDifferentContent(Path path, TrajectoryEntity trajectoryEntity) throws IOException {
        return getFileNameWithoutExtension(path.getFileName().toString()).equals(trajectoryEntity.getFileName())
                && (trajectoryEntity.getFileSize() != Files.size(path) || !trajectoryEntity.getChecksum().equals(getFileChecksum(path.toString())));
    }

    /**
     * Builds a trajectory from the given path to a file.
     *
     * @param path the path to the file to process
     * @return the built trajectory
     * @throws IOException if an I/O error occurs
     */
//    @ExecutionTime
    public static TrajectoryEntity buildTrajectory(Path path, int versionTrajectory, String horizon) throws IOException {
        return TrajectoryEntity.builder()
                .fileName(getFileNameWithoutExtension(path.getFileName().toString()))// file name without extension
                .fileSize(Files.size(path))
                .creationDate(LocalDateTime.now())
                .version(versionTrajectory == 0 ? 1 : versionTrajectory + 1)
                .checksum(getFileChecksum(path.toString()))
                .lastModificationContentDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(Files.getLastModifiedTime(path).toMillis()), ZoneId.systemDefault()))
                .horizon(horizon)
               // .warningMessage(warningMessages)
                .build();
    }

    /**
     * Checks the version of a trajectory by comparing the file name, file size, and checksum with a given TrajectoryEntity.
     * If the file has already been processed, a warning is logged and a RuntimeException is thrown.
     *
     * @param path             The path to the file to check the version of.
     * @param trajectoryEntity The TrajectoryEntity to compare the file to.
     * @throws IOException If an I/O error occurs reading from the file or a malformed or unmappable byte sequence is read.
     */
    public static boolean checkTrajectoryVersion(Path path, TrajectoryEntity trajectoryEntity) throws IOException {
        if (isSameFileWithDifferentContent(path, trajectoryEntity)) {
          log.info("File already processed but with different content : {}", path.getFileName());
            return true;
        } else if (isSameFileWithSameContent(path, trajectoryEntity)) {
            throw new AlreadyProcessedException("File already processed : " + path.getFileName());
        }
        return false;
    }

    public static String getFileNameWithoutExtension(String fileName) {
        Objects.requireNonNull(fileName);
        if (fileName.isBlank()) {
            throw new IllegalArgumentException("Empty fileName");
        }
        var lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex <= 0) { // takes into account files with already no extension or hidden files (.gitignore)
            return fileName;
        }

        return fileName.substring(0, lastDotIndex);
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


    public Object getCellValue(Row row, int cellIndex) {
        Cell cell = row.getCell(cellIndex);
        if (cell == null) {
            return null;
        }

        return switch (cell.getCellType()) {
            case NUMERIC -> cell.getNumericCellValue();
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> cell.getBooleanCellValue();
            case FORMULA -> cell.getCellFormula();
            default -> null;
        };
    }

    public void checkIfHorizonExist(Path path, String horizon) {
        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getSheet(horizon) == null)
                throw new TechnicalAntaresDataMangerException("The horizon " + horizon + " does not exist in the file :" + path.getFileName().toString());
        } catch (IOException e) {
            throw new TechnicalAntaresDataMangerException("could not check if horizon exist : " + e.getMessage());
        }
    }

    /**
     * Parses a date string to a LocalDateTime object.
     *
     * @param dateStr the string to be parsed, expected in the format "yyyy-MM-dd'T'HH:mm:ss"
     * @return a LocalDateTime object representing the parsed date and time
     * @throws TechnicalAntaresDataMangerException if the dateStr does not match the expected format
     */
    public static LocalDateTime parseToLocalDateTime(String dateStr) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
            return LocalDateTime.parse(dateStr, formatter);
        } catch (DateTimeParseException e) {
            throw new TechnicalAntaresDataMangerException("Invalid date format: " + e.getMessage());
        }
    }


    /**
     * Verifies if the provided string is in the expected date format.
     *
     * @param dateStr the string to verify, expected in the format "yyyy-MM-dd'T'HH:mm:ss"
     * @return true if dateStr can be parsed to LocalDateTime in the specified format; false otherwise
     *
     * TODO: Confirm the date format "yyyy-MM-dd'T'HH:mm:ss" with functional team.
     */
    public static boolean hasValidDateFormat(String dateStr) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
            LocalDateTime.parse(dateStr, formatter);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    public static String asJsonString(final Object obj) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper.writeValueAsString(obj);
    }

    /**
     * Ensures a file is of a certain extension
     * @param path file path
     * @param getFileExt Method to get the correct file format
     * @return The same path or the fixed one
     */
    public static Path ensureExtension(Path path, Supplier<String> getFileExt) {
        Objects.requireNonNull(path);
        Objects.requireNonNull(getFileExt);

        var ext = "." + getFileExt.get();
        return path.toString().endsWith(ext) ? path : path.resolveSibling(path.getFileName() + ext);
    }
}