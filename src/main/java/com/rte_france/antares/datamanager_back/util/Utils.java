package com.rte_france.antares.datamanager_back.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.hash.Hashing;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.model.ThermalCostTypeEntity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalCostsRateEntity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalModulationParameterEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.rte_france.antares.datamanager_back.dto.TrajectoryType.RES_CAPACITY;


/**
 * Utility class for file and trajectory related operations.
 */
@Slf4j
@UtilityClass
public class Utils {

    private static final String AREAS_PREFIX = "areas_";
    private static final String LINKS_PREFIX = "links_";
    private static final String THERMAL_PREFIX = "thermal_";
    private static final String THERMAL_COMMON_PREFIX = "common_param_";
    private static final String THERMAL_SPECIFIC_PREFIX = "specific_param_";
    private static final String THERMAL_ECONOMIC_PREFIX = "economic_param_";
    private static final String THERMAL_ECONOMIC_COST_PREFIX = "costs_";
    private static final String STS_PREFIX = "cluster_";
    private static final String DSR_PREFIX = "cluster_DSR_";
    private static final String DSR_CAPACITY_MODULATION = "cm_";
    private static final String MISC_CAPACITY_PREFIX = "installedMisc_";

    public static final String OTHERS_AREA = "OTHERS";

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
                && trajectoryEntity.getChecksum().equals(computeChecksumByType(path, TrajectoryType.valueOf(trajectoryEntity.getType()), trajectoryEntity.getHorizon(), trajectoryEntity.getArea()));
    }

    public static boolean isSameFileWithDifferentContent(Path path, TrajectoryEntity trajectoryEntity) throws IOException {
        return getFileNameWithoutExtensionAndWithoutPrefix(path.getFileName().toString(), trajectoryEntity.getType()).equals(trajectoryEntity.getFileName())
                && !trajectoryEntity.getChecksum().equals(computeChecksumByType(path, TrajectoryType.valueOf(trajectoryEntity.getType()), trajectoryEntity.getHorizon(), trajectoryEntity.getArea()));
    }

    public static boolean isSameTrajectory(Path path, TrajectoryEntity trajectoryEntity) throws IOException {
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

    public static List<String> getValidLoadFileNamesWithHorizon(Path dir, String area, String expectedHorizon, List<String> areaLoadAlreadyChosen, List<String> areaWithStudy) throws IOException {
        String areaPattern = area.equals(OTHERS_AREA) ? "[a-z0-9]+" : area.toLowerCase();
        Pattern pattern = Pattern.compile(String.format("load_(%s)_(\\d{4}-\\d{4})\\.txt", areaPattern));
        List<String> loadsFileNames = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.txt")) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString().toLowerCase();
                Matcher matcher = pattern.matcher(fileName);
                if (matcher.matches()) {
                    String areaFromFile = matcher.group(1);
                    String horizon = matcher.group(2);
                    boolean isValid = isValidLoadFile(horizon, expectedHorizon, areaFromFile, areaLoadAlreadyChosen, areaWithStudy);
                    if (isValid) {
                        loadsFileNames.add(fileName);
                    }
                }
            }
        }
        return loadsFileNames;
    }

    private static boolean isValidLoadFile(String horizon, String expectedHorizon, String areaFromFile, List<String> areaLoadAlreadyChosen, List<String> areaWithStudy) {
        boolean isAreaValid = areaWithStudy.contains(areaFromFile.toLowerCase());
        boolean isAreaNotChosen = areaLoadAlreadyChosen.isEmpty() || !areaLoadAlreadyChosen.contains(areaFromFile.toLowerCase());
        return horizon.equals(expectedHorizon) && isAreaValid && isAreaNotChosen;
    }

    /**
     * Builds a trajectory from the given path to a file.
     *
     * @param path the path to the file to process
     * @return the built trajectory
     * @throws IOException if an I/O error occurs
     */
    public static TrajectoryEntity buildTrajectory(Path path, int versionTrajectory, String horizon, String
            createdBy, TrajectoryType trajectoryType, String area, String technology, Boolean hasSeries) throws IOException {
        String checksum = computeChecksumByType(path, trajectoryType, horizon, area);
        return TrajectoryEntity.builder()
                .fileName(getFileNameWithoutExtensionAndWithoutPrefix(path.getFileName().toString(), trajectoryType.name()))// file name without extension
                .fileSize(Files.size(path))
                .creationDate(LocalDateTime.now())
                .createdBy(createdBy)
                .version(versionTrajectory == 0 ? 1 : versionTrajectory + 1)
                .checksum(checksum)
                .lastModificationContentDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(Files.getLastModifiedTime(path).toMillis()), ZoneId.systemDefault()))
                .horizon(civilToChevalHorizon(horizon))
                .area(area)
                .technology(Optional.ofNullable(technology).map(t -> StringUtils.lowerCase(t, Locale.ROOT)).orElse(null))
                .type(trajectoryType.name())
                .hasTimeSeries(Boolean.TRUE.equals(hasSeries))
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

    public static boolean checkParamModulationTrajectoryVersion(
            List<ThermalModulationParameterEntity> thermalModulationParameterEntityList,
            TrajectoryEntity trajectoryEntity) {

        if (thermalModulationParameterEntityList == null || thermalModulationParameterEntityList.isEmpty() ||
                trajectoryEntity.getThermalModulationParameters() == null || trajectoryEntity.getThermalModulationParameters().isEmpty()) {
            return false;
        }

        String newCmChecksum = extractChecksum(thermalModulationParameterEntityList, "CM_");
        String newMrChecksum = extractChecksum(thermalModulationParameterEntityList, "MR_");

        String existingCmChecksum = extractChecksum(trajectoryEntity.getThermalModulationParameters(), "CM_");
        String existingMrChecksum = extractChecksum(trajectoryEntity.getThermalModulationParameters(), "MR_");

        boolean sameCm = Objects.equals(newCmChecksum, existingCmChecksum);
        boolean sameMr = Objects.equals(newMrChecksum, existingMrChecksum);

        if (sameCm && sameMr) {
            throw BusinessException.builder()
                    .message("File already processed with same content : {0}")
                    .errorMessageArguments(List.of(trajectoryEntity.getFileName()))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        return true;
    }

    private static String extractChecksum(List<ThermalModulationParameterEntity> entities, String prefix) {
        return entities.stream()
                .filter(param -> param.getTsName().startsWith(prefix))
                .map(ThermalModulationParameterEntity::getChecksum)
                .findFirst()
                .orElse(null);
    }

    private static String removeClusterPrefix(String fileName) {
        // enlève un préfixe du type "cluster_<techno>_" au début du nom, insensible à la casse
        return fileName.replaceFirst("(?i)^cluster_[^_]+_", "");
    }

    public static String getFileNameWithoutExtensionAndWithoutPrefix(String fileName, String trajectoryType) {
        Objects.requireNonNull(fileName);
        if (fileName.isBlank()) {
            throw TechnicalException.builder().message("Empty fileName").build();
        }
        String prefix;
        if (Objects.equals(trajectoryType, TrajectoryType.AREA.toString())) {
            prefix = AREAS_PREFIX;
        } else if (Objects.equals(trajectoryType, TrajectoryType.LINK.toString())) {
            prefix = LINKS_PREFIX;
        } else if (Objects.equals(trajectoryType, TrajectoryType.THERMAL_CAPACITY.toString())) {
            prefix = THERMAL_PREFIX;
        } else if (Objects.equals(trajectoryType, TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.toString())) {
            prefix = THERMAL_COMMON_PREFIX;
        } else if (Objects.equals(trajectoryType, TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER.toString())) {
            prefix = THERMAL_SPECIFIC_PREFIX;
        } else if (Objects.equals(trajectoryType, TrajectoryType.THERMAL_ECONOMIC_PARAMETER.toString())) {
            prefix = THERMAL_ECONOMIC_PREFIX;
        } else if (Objects.equals(trajectoryType, TrajectoryType.THERMAL_ECONOMIC_COST_PARAMETER.toString())) {
            prefix = THERMAL_ECONOMIC_COST_PREFIX;
        } else if (Objects.equals(trajectoryType, TrajectoryType.DSR.toString())) {
            prefix = DSR_PREFIX;
        } else if (Objects.equals(trajectoryType, TrajectoryType.DSR_CAPACITY_MODULATION.toString())) {
            prefix = DSR_CAPACITY_MODULATION;
        } else if (Objects.equals(trajectoryType, TrajectoryType.MISC_CAPACITY.toString())) {
            prefix = MISC_CAPACITY_PREFIX;
        } else {
            prefix = "";
        }

        if (Objects.equals(trajectoryType, TrajectoryType.STS.toString())) {
            fileName = removeClusterPrefix(fileName);
        } else if (!prefix.isEmpty() && fileName.toLowerCase().startsWith(prefix.toLowerCase())) {
            fileName = fileName.substring(prefix.length());
        }

        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex <= 0) {
            return fileName;
        }
        return fileName.substring(0, lastDotIndex);
    }


    /**
     * Finds a sheet in the provided workbook that matches the given horizon name.
     * The search attempts an exact match with the horizon name if it is not null or blank.
     *
     * @param workbook the workbook to search in; must not be null
     * @param horizon  the name of the horizon to locate; can be null or blank
     * @return the sheet matching the horizon name, or null if no match is found
     */
    public static Sheet findHorizonSheet(Workbook workbook, String horizon) {
        if (workbook == null) return null;

        if (horizon != null && !horizon.isBlank()) {
            return workbook.getSheet(horizon);

        }
        return null;
    }


    public static Object getCellValue(Row row, int cellIndex) {
        Cell cell = row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return null;
        }

        return switch (cell.getCellType()) {
            case NUMERIC -> cell.getNumericCellValue();
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> cell.getBooleanCellValue();
            case FORMULA -> {
                FormulaEvaluator evaluator = row.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
                CellValue evaluated = evaluator.evaluate(cell);
                if (evaluated == null) {
                    yield null;
                }
                yield switch (evaluated.getCellType()) {
                    case NUMERIC -> evaluated.getNumberValue();
                    case STRING -> evaluated.getStringValue();
                    case BOOLEAN -> evaluated.getBooleanValue();
                    default -> null; // BLANK or ERROR
                };
            }
            default -> null;
        };
    }

    public void checkIfHorizonExist(Path path, String horizon, String trajectoryType) {
        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getSheet(horizon) == null) {
                throw BusinessException.builder()
                        .message("Horizon {0} does not exist in the {1} trajectory")
                        .errorMessageArguments(List.of(horizon, trajectoryType))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

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
     * Calcule le checksum SHA-256 d'un fichier de trajectoire en fonction de son type
     *
     * @param path    chemin vers le fichier .xlsx
     * @param type    type de la trajectoire
     * @param horizon horizon concerné
     * @return hash SHA-256 sous forme hexadécimale
     * @throws IOException en cas de fichier introuvable ou feuille absente
     */
    public static String computeChecksumByType(Path path, TrajectoryType type, String horizon, String area) throws IOException {
        return switch (type) {
            case LOAD, THERMAL_CAPACITY -> getFileChecksum(path.toString());
            case LINK -> computeLinkChecksum(path.toString(), horizon);
            case THERMAL_TECHNICAL_MODULATION_PARAMETER, THERMAL_ECONOMIC_COST_PARAMETER, THERMAL_ECONOMIC_PARAMETER ->
                    "NA";
            case STS ->
                    computeSheetChecksum(path.toString(), horizon.matches("^\\d{4}-\\d{4}$") ? horizon.split("-")[1] : horizon);
            case DSR ->
                    computeDsrChecksum(path.toString(), horizon.matches("^\\d{4}-\\d{4}$") ? horizon.split("-")[1] : horizon, area);
            case MISC_CAPACITY -> "checksum_misc";
            default -> computeSheetChecksum(path.toString(), horizon);
        };
    }

    private String canonicalRow(Row row) {
        StringBuilder sb = new StringBuilder();

        for (int c = 0; c < row.getLastCellNum(); c++) {
            sb.append(getCellValue(row, c)).append("|");
        }

        return sb.toString();
    }

    private String hashSheetByArea(Sheet sheet, String areaFilter) {
        var sb = new StringBuilder();

        for (int i = 1; i <= sheet.getLastRowNum(); i++) { // skip header
            Row row = sheet.getRow(i);
            if (row == null) continue;

            Cell areaCell = row.getCell(1); // 2ème colonne
            if (areaCell == null) continue;

            String area = areaCell.getStringCellValue();
            if (!areaFilter.equals(area)) continue;

            // Construire une ligne canonique
            sb.append(canonicalRow(row)).append("\n");
        }

        return Hashing.sha256().hashString(sb.toString(), StandardCharsets.UTF_8).toString();
    }

    private static String computeDsrChecksum(String filePath, String horizon, String areaFilter) throws IOException {
        try (var in = Files.newInputStream(Path.of(filePath));
             var wb = WorkbookFactory.create(in)) {

            var horizonSheet = wb.getSheet(horizon);
            if (horizonSheet == null) {
                throw TechnicalException.builder()
                        .message("Sheet '" + horizon + "' not found: " + filePath)
                        .build();
            }

            // Hash uniquement des lignes correspondant à l'area
            var hHash = hashSheetByArea(horizonSheet, areaFilter);

            return Hashing.sha256()
                    .hashString(hHash, StandardCharsets.UTF_8)
                    .toString();
        }
    }

    public static String computeSheetChecksum(String filePath, String sheetName) throws IOException {
        try (InputStream in = Files.newInputStream(Path.of(filePath));
             Workbook wb = WorkbookFactory.create(in)) {

            Sheet sheet = wb.getSheet(sheetName);
            if (sheet == null) {
                throw TechnicalException.builder()
                        .message("Feuille '" + sheetName + "' non trouvée dans le fichier : " + filePath)
                        .build();
            }
            return hashWholeSheet(sheet);
        }
    }

    private static String cellToString(Cell c) {
        return switch (c.getCellType()) {
            case STRING -> c.getStringCellValue();
            case NUMERIC -> String.valueOf(c.getNumericCellValue());
            case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
            case FORMULA -> c.getCellFormula();
            case BLANK -> "BLANK";
            default -> "NULL";
        };
    }

    private static String hashWholeSheet(Sheet sheet) {
        var sb = new StringBuilder();
        for (var row : sheet) {
            for (var cell : row) {
                sb.append(cellToString(cell)).append('|');
            }
            sb.append('\n');
        }
        return Hashing.sha256().hashString(sb.toString(), StandardCharsets.UTF_8).toString();
    }

    private static String hashColumnByHeader(Sheet sheet, String headerNameRaw) {
        var headerName = headerNameRaw.trim();
        var header = sheet.getRow(0);
        if (header == null) {
            throw TechnicalException.builder().message("Header row missing in sheet '" + sheet.getSheetName() + "'").build();
        }

        var col = -1;
        for (var c : header) {
            if (c != null && c.getCellType() == CellType.STRING
                    && headerName.equalsIgnoreCase(c.getStringCellValue().trim())) {
                col = c.getColumnIndex();
                break;
            }
        }
        if (col < 0) {
            throw TechnicalException.builder().message(
                    "Column '" + headerName + "' not found in header of sheet '" + sheet.getSheetName() + "'").build();
        }

        var entries = new ArrayList<String>();
        for (var r = 1; r <= sheet.getLastRowNum(); r++) {
            var row = sheet.getRow(r);
            if (row == null) continue;

            var key = "ROW_" + r;
            var k = row.getCell(0);
            if (k != null && k.getCellType() == CellType.STRING) key = k.getStringCellValue().trim();

            entries.add(key + "=" + cellToString(row.getCell(col)));
        }
        entries.sort(String.CASE_INSENSITIVE_ORDER);
        return Hashing.sha256().hashString(String.join("\n", entries), StandardCharsets.UTF_8).toString();
    }

    private static String computeLinkChecksum(String filePath, String horizon) throws IOException {
        try (var in = Files.newInputStream(Path.of(filePath));
             var wb = WorkbookFactory.create(in)) {

            var horizonSheet = wb.getSheet(horizon);
            if (horizonSheet == null) {
                throw TechnicalException.builder().message("Sheet '" + horizon + "' not found: " + filePath).build();
            }
            var hHash = hashWholeSheet(horizonSheet);

            var params = wb.getSheet("parameters");
            if (params == null) {
                throw TechnicalException.builder().message("Sheet 'parameters' not found: " + filePath).build();
            }
            var pHash = hashColumnByHeader(params, horizon);

            return Hashing.sha256().hashString(hHash + pHash, StandardCharsets.UTF_8).toString();
        }
    }


    /**
     * Extracts the area identifier from a given file name.
     * The expected file name format is "load_AREA_HORIZON.txt", where "AREA" represents the area identifier.
     * If the file name does not match the expected format or "load" is not the prefix, the method returns null.
     *
     * @param fileName The name of the file from which the area identifier should be extracted.
     * @return The extracted area identifier from the file name, or null if the format is invalid or the prefix is incorrect.
     */
    public static String extractAreaFromFileName(String fileName) {

        String[] parts = fileName.split("_");
        if (parts.length >= 3 && parts[0].equals("load")) {
            return parts[1]; // Retourne la partie "area" (ex: "at" pour "load_at_2030-2031.txt")
        }
        return null;
    }

    /**
     * Normalize a string (to upper case)
     *
     * @param s string
     * @return normalized string s
     */
    public static String normalize(String s) {
        Objects.requireNonNull(s);
        return s.trim().toUpperCase(Locale.ROOT);
    }

    public static Sheet findHorizonSheetOrThrow(Workbook workbook, String horizon) {
        Sheet sheet = findHorizonSheet(workbook, horizon);
        if (sheet == null) {
            throw BusinessException.builder()
                    .message("Missing suitable sheet for horizon '" + horizon + "'")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        return sheet;
    }

    /**
     * Finds the column index whose header matches the horizon string exactly.
     */
    public static Integer findHorizonColumnIndex(Row header, String horizon) {
        for (int i = 0; i < header.getLastCellNum(); i++) {
            Cell cell = header.getCell(i);
            if (cell != null) {
                String headerValue = getHeaderValue(cell);
                if (headerValue != null && headerValue.equals(horizon)) {
                    return i;
                }
            }
        }
        return null;
    }


    private String getHeaderValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double numValue = cell.getNumericCellValue();

                if (numValue == Math.floor(numValue)) {
                    yield String.valueOf((int) numValue);
                } else {
                    yield String.valueOf(numValue);
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    public String calculateThermalCostTrajectoryChecksum(List<ThermalCostTypeEntity> thermalCostsType, List<ThermalCostsRateEntity> thermalRates) {

        String thermalCostsPart =
                thermalCostsType == null ? "" :
                        thermalCostsType.stream()
                                .filter(Objects::nonNull)
                                .flatMap(costType -> costType.getThermalCostEntities().stream()
                                        .filter(Objects::nonNull)
                                        .map(cost -> new StringBuilder()
                                                .append(cost.getCost())
                                                .append(cost.getYear())
                                                .append(nullSafe(costType.getCountry()))
                                                .append(nullSafe(costType.getFuel()))
                                                .append(nullSafe(costType.getComment()))
                                                .append(nullSafe(costType.getUnit()))
                                                .append(nullSafe(costType.getModulation()))
                                                .append(nullSafe(costType.getRatioNcvHcv()))
                                                .append(";")
                                                .toString()
                                        )
                                )
                                .collect(Collectors.joining());

        String thermalRatesPart =
                thermalRates == null ? "" :
                        thermalRates.stream()
                                .filter(Objects::nonNull)
                                .map(rate -> new StringBuilder()
                                        .append(rate.getRateType())
                                        .append(rate.getValue())
                                        .append(rate.getYear())
                                        .append("|")
                                        .toString()
                                )
                                .collect(Collectors.joining());

        String finalString = thermalCostsPart + thermalRatesPart;

        return Integer.toHexString(finalString.hashCode());
    }

    private static String nullSafe(Object o) {
        return o == null ? "" : o.toString();
    }

    public static Optional<Path> findFile(Path directory, String fileName) throws IOException {
        Path baseDir = directory.toRealPath().normalize();

        // Reject dangerous path input
        Path target = baseDir.resolve(fileName).normalize();
        if (!target.startsWith(baseDir)) {
            throw new SecurityException("Invalid file path: path traversal attempt detected");
        }

        try (var files = Files.list(baseDir)) {
            return files
                    .filter(p -> p.getFileName().toString().equals(target.getFileName().toString()))
                    .findFirst();
        }
    }

    public static Path findChildDirectoryIgnoreCase(Path parent, String childName) throws IOException {
        try (Stream<Path> s = Files.list(parent)) {
            Optional<Path> dir = s.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(childName))
                    .findFirst();

            if (dir.isPresent()) {
                return dir.get();
            } else {
                throw BusinessException.builder().message("Directory not found under " + extractStsPathFromErrorMessage(parent.toString()) + " for '" + childName + "'").build();
            }
        }
    }

    // java
    public static String extractStsPathFromErrorMessage(String errorMessage) {
        if (errorMessage == null) return null;
        Pattern p = Pattern.compile("(?i)INPUT/([^\\s']+)(?:\\sfor|$)");
        Matcher m = p.matcher(errorMessage);
        return m.find() ? m.group(1) : errorMessage;
    }

    public static String civilToChevalHorizon(String horizon) {
        if (horizon == null) return null;
        String s = horizon.trim();
        if (s.length() == 4 && s.chars().allMatch(Character::isDigit)) {
            int year = Integer.parseInt(s);
            return String.format("%04d-%s", year - 1, s);
        }
        return s;
    }

    public boolean isNumericCell(Cell cell) {
        if (cell == null) return false;
        CellType t = cell.getCellType();
        if (t == CellType.NUMERIC) return true;
        if (t == CellType.FORMULA) {
            CellType resType = cell.getCachedFormulaResultType();
            return resType == CellType.NUMERIC;
        }
        if (t == CellType.STRING) {
            String s = cell.getStringCellValue().trim();
            if (s.isEmpty()) return false;
            try {
                Double.parseDouble(s);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    public static void checkMissingColumns(Sheet sheet, String[] expectedColumns, String trajectoryName, String type) {
        Row headerRow = sheet.getRow(0);
        List<String> missingColumns = new ArrayList<>();
        if (headerRow == null) {
            missingColumns.addAll(Arrays.asList(expectedColumns));
        } else {
            int lastCell = headerRow.getLastCellNum() < 0 ? 0 : headerRow.getLastCellNum();
            Set<String> headerNames = new HashSet<>();
            for (int i = 0; i < lastCell; i++) {
                Cell c = headerRow.getCell(i);
                if (c != null) {
                    String val = c.toString().trim().toLowerCase(Locale.ROOT);
                    if (!val.isEmpty()) headerNames.add(val);
                }
            }
            for (String expected : expectedColumns) {
                String norm = expected.trim().toLowerCase(Locale.ROOT);
                if (!headerNames.contains(norm)) {
                    missingColumns.add(expected);
                }
            }
        }
        if (!missingColumns.isEmpty()) {
            String missingList = String.join(", ", missingColumns);
            throw BusinessException.builder().message("Missing columns " + missingList + " in " + type + " trajectory " + trajectoryName).build();
        }
    }

    public static boolean startsWithIgnoreCase(String name, String prefix) {
        if (name == null) {
            return false;
        }
        return name.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    public Sheet getRequiredSheet(Workbook workbook, String horizon, Path trajectoryFilePath, String trajectoryType) {
        Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheet(horizon) : null;
        if (sheet == null) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(horizon, trajectoryFilePath.getFileName().toString()))
                    .message("Horizon {0} does not exist in the " + trajectoryType + " trajectory {1}")
                    .build();
        }
        return sheet;
    }

    // Méthode utilitaire pour calculer le checksum SHA-256
    public String calculateChecksum(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Erreur lors du calcul du checksum", e);
        }
    }

    public static void throwAlreadyProcessedFileException(Path path) {
        log.info("Le contenu du fichier {} n'a pas changé par rapport à la dernière version enregistrée.", path.getFileName());
        throw BusinessException.builder()
                .message("File already processed with same content {0}")
                .errorMessageArguments(List.of(path.getFileName().toString()))
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build();
    }

    public static String calculateDirectoryChecksum(Path directory)
            throws IOException, NoSuchAlgorithmException {

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (Stream<Path> paths = Files.walk(directory)) {

            List<Path> files = paths
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString)) // ordre stable
                    .toList();

            for (Path file : files) {

                // Hash du chemin relatif (important)
                Path relativePath = directory.relativize(file);
                digest.update(relativePath.toString().getBytes());

                // Hash du contenu du fichier
                try (InputStream is = Files.newInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        digest.update(buffer, 0, bytesRead);
                    }
                }
            }
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    public static void validateTrajectoryAreasPresence(List<String> studyAreas, List<String> fileAreas, TrajectoryType trajectoryType, String trajectoryToUse) {
        boolean hasNoAreaOfTrajectoryAreaInFile = studyAreas.stream().noneMatch(fileAreas::contains);
        if (hasNoAreaOfTrajectoryAreaInFile) {
            String label = getErrorMessageLabelFromType(trajectoryType);
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(label, trajectoryToUse))
                    .message("None of the areas of trajectory AREA are present in {0} trajectory {1}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }
    
    private static String getErrorMessageLabelFromType(TrajectoryType type) {
        return switch (type) {
            case DSR -> "DSR cluster";
            case MISC_CAPACITY -> "MISC";
            default -> "trajectory";
        };
    }

    public static void validateSelectedAreaPresence(String areaParam, List<String> fileAreas, TrajectoryType trajectoryType, String trajectoryFileName) {
        if (!areaParam.isBlank() && !OTHERS_AREA.equals(areaParam) && !fileAreas.contains(areaParam.toUpperCase())) {
            String label = getErrorMessageLabelFromType(trajectoryType);
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(areaParam, label, trajectoryFileName))
                    .message("Selected area {0} is not present in the 'node' column of {1} trajectory {2}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    public Stream<Path> findResCapacityTechnologyFiles(Path directoryPath, String prefix, String technology) throws IOException {
            String prefixWithTechnology = prefix + technology.toLowerCase() + "_";
            return Files.walk(directoryPath, 2)
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.startsWith(prefixWithTechnology) && name.endsWith(".xlsx");
                    });
        }
}