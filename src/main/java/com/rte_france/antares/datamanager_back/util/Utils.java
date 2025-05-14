package com.rte_france.antares.datamanager_back.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.hash.Hashing;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Utility class for file and trajectory related operations.
 */
@Slf4j
@UtilityClass
public class Utils {

    private static final String AREAS_PREFIX = "areas_";
    private static final String LINKS_PREFIX = "links_";

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
            throw TechnicalException.builder()
                    .message("could not get file checksum : {0}")
                    .errorMessageArguments(List.of(filePath))
                    .build();
        }
    }


    public static boolean isSameFileWithSameContent(Path path, TrajectoryEntity trajectoryEntity) throws IOException {
        return getFileNameWithoutExtensionAndWithoutPrefix(path.getFileName().toString(), trajectoryEntity.getType()).equals(trajectoryEntity.getFileName())
                && trajectoryEntity.getChecksum().equals(computeSheetChecksum(path.toString(), trajectoryEntity.getHorizon()));
    }

    public static boolean isSameFileWithDifferentContent(Path path, TrajectoryEntity trajectoryEntity) throws IOException {
        return getFileNameWithoutExtensionAndWithoutPrefix(path.getFileName().toString(), trajectoryEntity.getType()).equals(trajectoryEntity.getFileName())
                && !trajectoryEntity.getChecksum().equals(computeSheetChecksum(path.toString(), trajectoryEntity.getHorizon()));
    }

    public static boolean isSameLoadTrajectory(Path path, TrajectoryEntity trajectoryEntity) throws IOException {
        return getFileNameWithoutExtensionAndWithoutPrefix(path.getFileName().toString(), trajectoryEntity.getType()).equals(trajectoryEntity.getFileName())
                && (trajectoryEntity.getLastModificationContentDate()
                .truncatedTo(ChronoUnit.SECONDS)
                .isEqual(Files.getLastModifiedTime(path)
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime()
                        .truncatedTo(ChronoUnit.SECONDS)))
                ;
    }

    public static List<String> getValidLoadFileNamesWithHorizon(Path dir, String area, String expectedHorizon) throws IOException {
        String areaPattern = area.equals("EU") ? "[a-z]{2}" : area.toLowerCase();
        Pattern pattern = Pattern.compile("load_" + areaPattern + "_(\\d{4}-\\d{4})\\.txt");
        List<String> loadsFileNames = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.txt")) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                Matcher matcher = pattern.matcher(fileName);
                if (matcher.matches()) {
                    String horizon = matcher.group(1); // extract horizon
                    if (horizon.equals(expectedHorizon)) {
                        loadsFileNames.add(fileName);
                    }
                }
            }
        }

        return loadsFileNames;
    }

    /**
     * Builds a trajectory from the given path to a file.
     *
     * @param path the path to the file to process
     * @return the built trajectory
     * @throws IOException if an I/O error occurs
     */
    public static TrajectoryEntity buildTrajectory(Path path, int versionTrajectory, String horizon, String createdBy, TrajectoryType trajectoryType) throws IOException {
        return TrajectoryEntity.builder()
                .fileName(getFileNameWithoutExtensionAndWithoutPrefix(path.getFileName().toString(), trajectoryType.name()))// file name without extension
                .fileSize(Files.size(path))
                .creationDate(LocalDateTime.now())
                .createdBy(createdBy)
                .version(versionTrajectory == 0 ? 1 : versionTrajectory + 1)
                .checksum(trajectoryType.name().equals(TrajectoryType.LOAD.name()) || trajectoryType.name().equals(TrajectoryType.THERMAL_CAPACITY.name()) ? getFileChecksum(path.toString()) : computeSheetChecksum(path.toString(), horizon))
                .lastModificationContentDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(Files.getLastModifiedTime(path).toMillis()), ZoneId.systemDefault()))
                .horizon(horizon)
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
            throw BusinessException.builder()
                    .message("File already processed  with same content : {0}")
                    .errorMessageArguments(List.of(path.getFileName().toString()))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        return false;
    }

    public static String getFileNameWithoutExtensionAndWithoutPrefix(String fileName, String trajectoryType) {
        Objects.requireNonNull(fileName);
        if (fileName.isBlank()) {
            throw TechnicalException.builder().message("Empty fileName").build();
        }
        String prefix = Objects.equals(trajectoryType, TrajectoryType.AREA.toString()) ? AREAS_PREFIX :
                isLinkTypePrefix(trajectoryType);
        if (!prefix.isEmpty() && fileName.startsWith(prefix)) {
            fileName = fileName.substring(prefix.length());

        }
        var lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex <= 0) {
// takes into account files with already no extension or hidden files (.gitignore)
            return fileName;
        }
        return fileName.substring(0, lastDotIndex);
    }

    private static String isLinkTypePrefix(String trajectoryType) {
        return Objects.equals(trajectoryType, TrajectoryType.LINK.toString()) ? LINKS_PREFIX : "";
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

    public void checkIfHorizonExist(Path path, String horizon, String trajectoryType) {
        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getSheet(horizon) == null)
                throw BusinessException.builder()
                        .message("Horizon {0} does not exist in the {1} trajectory file : {2}")
                        .errorMessageArguments(List.of(horizon, trajectoryType, path.getFileName().toString()))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
        } catch (IOException e) {
            throw TechnicalException.builder()
                    .message("could not check if horizon exist : {0}")
                    .errorMessageArguments(List.of(horizon, path.getFileName().toString()))
                    .cause(e.getCause())
                    .build();
        }
    }

    /**
     * Parses a string to a LocalDateTime object.
     *
     * @param dateStr the string to parse, expected in the format "yyyy-MM-dd'T'HH:mm:ss"
     * @return the parsed LocalDateTime object
     * @throws TechnicalException if the string cannot be parsed to a LocalDateTime
     */
    public static LocalDateTime parseToLocalDateTime(String dateStr) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
            return LocalDateTime.parse(dateStr, formatter);
        } catch (DateTimeParseException e) {
            throw TechnicalException.builder()
                    .message("Invalid date format")
                    .cause(e.getCause())
                    .build();
        }
    }


    /**
     * Verifies if the provided string is in the expected date format.
     *
     * @param dateStr the string to verify, expected in the format "yyyy-MM-dd'T'HH:mm:ss"
     * @return true if dateStr can be parsed to LocalDateTime in the specified format; false otherwise
     * <p>
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
     *
     * @param path       file path
     * @param getFileExt Method to get the correct file format
     * @return The same path or the fixed one
     */
    public static Path ensureExtension(Path path, Supplier<String> getFileExt) {
        Objects.requireNonNull(path);
        Objects.requireNonNull(getFileExt);

        var ext = "." + getFileExt.get();
        return path.toString().endsWith(ext) ? path : path.resolveSibling(path.getFileName() + ext);
    }


    /**
     * Calcule le checksum SHA-256 d'une feuille Excel par nom
     *
     * @param filePath  chemin vers le fichier .xlsx
     * @param sheetName nom exact de la feuille Excel
     * @return hash SHA-256 sous forme hexadécimale
     * @throws IOException en cas de fichier introuvable ou feuille absente
     */
    public static String computeSheetChecksum(String filePath, String sheetName) throws IOException {
        try (InputStream inputStream = Files.newInputStream(Path.of(filePath));
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw TechnicalException.builder().message("Feuille '" + sheetName + "' non trouvée dans le fichier : " + filePath).build();
            }

            StringBuilder sb = new StringBuilder();

            for (Row row : sheet) {
                for (Cell cell : row) {
                    switch (cell.getCellType()) {
                        case STRING -> sb.append(cell.getStringCellValue());
                        case NUMERIC -> sb.append(cell.getNumericCellValue());
                        case BOOLEAN -> sb.append(cell.getBooleanCellValue());
                        case FORMULA -> sb.append(cell.getCellFormula());
                        case BLANK -> sb.append("BLANK");
                        default -> sb.append("NULL");
                    }
                    sb.append("|");
                }
                sb.append("\n");
            }

            return Hashing.sha256()
                    .hashString(sb.toString(), StandardCharsets.UTF_8)
                    .toString();
        }
    }
}