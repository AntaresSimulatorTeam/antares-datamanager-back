package com.rte_france.antares.datamanager_back.service.area_link.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.repository.LinkMeRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.WarningRepository;
import com.rte_france.antares.datamanager_back.repository.model.LinkMeEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.util.ExecutionTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

import static com.rte_france.antares.datamanager_back.util.Utils.*;

/**
 * Service for importing LINK_ME trajectories.
 * Implements specific business rules for LINK_ME type.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LinkMeProcessorServiceImpl {

    private final TrajectoryRepository trajectoryRepository;
    private final LinkMeRepository linkMeRepository;
    private final WarningRepository warningRepository;
    private final UserService userService;
    private final AntaresDataManagerProperties antaresDataManagerProperties;

    private static final int MAX_TRAJECTORY_NAME_LENGTH = 40;

    /**
     * Processes a LINK_ME trajectory file by importing it into the database.
     * Handles the following business rules:
     * - One sheet per horizon in format YYYY (representing YYYY+1 in Pegase format YYYY-YYYY+1)
     * - Header in line 1, data from line 2 onwards
     * - Check for duplicate file with same checksum
     * - Trajectory name must not exceed 40 characters
     * - Must have a sheet for the study horizon
     * - nodeFrom and nodeTo columns must not be null
     * - nodeFrom and nodeTo must not exceed 60 characters
     * - Direct_MW and Indirect_MW must be numeric or 'infinite'
     * - Hurdle Costs Direct and Indirect must be numeric
     *
     * @param trajectoryToUse the name of the trajectory (directory name)
     * @param horizon         the horizon period in the format yyyy-yyyy
     * @param studyId         the ID of the study
     * @return the processed TrajectoryEntity
     * @throws IOException if an I/O error occurs
     */
    @ExecutionTime
    @Transactional
    public TrajectoryEntity processLinkMeFile(String trajectoryToUse, String horizon, Integer studyId) throws IOException {
        Path trajectoryFilePath = getTrajectoryFilePath(trajectoryToUse);
        return importLinkMeTrajectory(trajectoryFilePath, horizon, trajectoryToUse);
    }

    /**
     * Builds the trajectory file path for LINK_ME type.
     * @param trajectoryToUse the trajectory name (directory name)
     * @return the resolved file path
     * @throws IOException if path construction fails
     */
    public Path getTrajectoryFilePath(String trajectoryToUse) throws IOException {
        Path baseDirectory = Path.of(antaresDataManagerProperties.getNasDirectory())
                .resolve(antaresDataManagerProperties.getTrajectoryFilePath())
                .resolve(antaresDataManagerProperties.getLinkMeDirectory())
                .normalize();

        if (!baseDirectory.endsWith("/")) {
            baseDirectory = baseDirectory.resolve("");
        }

        Path trajectoryFilePath = baseDirectory.resolve(trajectoryToUse + ".xlsx").normalize();
        if (!trajectoryFilePath.startsWith(baseDirectory)) {
            throw new IOException("Path is outside of the target directory");
        }
        return trajectoryFilePath;
    }

    /**
     * Imports a LINK_ME trajectory file.
     * @param path path to the LINK_ME file
     * @param horizon horizon in format YYYY-YYYY+1 (e.g., 2023-2024)
     * @param trajectoryName trajectory name (directory name)
     * @return the imported trajectory entity
     */
    @ExecutionTime
    @Transactional
    public TrajectoryEntity importLinkMeTrajectory(Path path, String horizon, String trajectoryName) throws IOException {
        log.info("Importing LINK_ME trajectory: {} with horizon: {}", trajectoryName, horizon);

        // Rule 1: Extract sheet name (YYYY from YYYY-YYYY+1)
        String sheetName = extractSheetName(horizon);

        // Rule 2: Validate trajectory name length (max 40 characters)
        validateTrajectoryNameLength(trajectoryName);

        // Rule 3: Verify sheet exists in file
        verifySheetExists(path, sheetName, trajectoryName);

        // Rule 4: Calculate checksum for deduplication
        String checksum = computeChecksumByType(path, TrajectoryType.LINK_ME, horizon, "");

        // Rule 5: Check if file already processed with same content
        checkForDuplicateChecksum(trajectoryName, horizon, checksum);

        // Rule 6-7: Validate and build LINK_ME entities from file (single file open)
        List<LinkMeEntity> linkMeEntities = buildAndValidateLinkMeEntities(path, sheetName, trajectoryName);

        // Rule 8: Create trajectory entity
        String createdBy = userService.getCurrentUserDetails().getNni();
        TrajectoryEntity trajectory = createTrajectoryEntity(trajectoryName, horizon, createdBy, checksum);

        // Rule 9: Save trajectory and associated LINK_ME entities
        TrajectoryEntity savedTrajectory = trajectoryRepository.save(trajectory);
        saveLinkMeEntities(savedTrajectory, linkMeEntities);

        log.info("Successfully imported LINK_ME trajectory: {} with {} links", trajectoryName, linkMeEntities.size());
        return savedTrajectory;
    }

    /**
     * Rule 1: Extract sheet name from horizon format YYYY-YYYY+1
     * Example: "2023-2024" → "2024"
     */
    private String extractSheetName(String horizon) {
        String[] parts = horizon.split("-");
        if (parts.length != 2) {
            throw BusinessException.builder()
                    .message("Invalid horizon format. Expected YYYY-YYYY+1")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        return parts[1];
    }

    /**
     * Rule 2: Validate trajectory name length (max 40 characters)
     */
    private void validateTrajectoryNameLength(String trajectoryName) {
        if (trajectoryName == null || trajectoryName.isEmpty()) {
            throw BusinessException.builder()
                    .message("Trajectory name cannot be empty")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        if (trajectoryName.length() > MAX_TRAJECTORY_NAME_LENGTH) {
            throw BusinessException.builder()
                    .message("Trajectory name cannot exceed 40 characters")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    /**
     * Rule 3: Verify sheet exists in Excel file
     */
    public void verifySheetExists(Path path, String sheetName, String trajectoryName) {
        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw BusinessException.builder()
                        .message("Missing horizon {0} in LINKS_ME trajectory {1}")
                        .errorMessageArguments(List.of(sheetName, trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

        } catch (IOException e) {
            throw TechnicalException.builder()
                    .message("Could not read LINKS_ME trajectory file: {0}")
                    .errorMessageArguments(List.of(path.getFileName().toString()))
                    .cause(e)
                    .build();
        }
    }


    /**
     * Rule 5: Check if file already processed with same checksum
     */
    public void checkForDuplicateChecksum(String trajectoryName, String horizon, String checksum) {
        Optional<TrajectoryEntity> existingTrajectory = trajectoryRepository
                .findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                        trajectoryName, horizon, TrajectoryType.LINK_ME.name()
                );

        if (existingTrajectory.isPresent()) {
            String existingChecksum = existingTrajectory.get().getChecksum();
            if (existingChecksum != null && existingChecksum.equals(checksum)) {
                throw BusinessException.builder()
                        .message("File already processed with same content : {0}")
                        .errorMessageArguments(List.of(trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
        }
    }

    /**
     * Rule 6-7: Validates and builds LINK_ME entities from file (opens file only once)
     * Iterates through rows starting from row 2 (skip header at row 1)
     * Validates columns during row processing
     */
    private List<LinkMeEntity> buildAndValidateLinkMeEntities(Path path, String sheetName, String trajectoryName) {
        List<LinkMeEntity> linkMeEntities = new ArrayList<>();

        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw BusinessException.builder()
                        .message("Missing horizon {0} in LINKS_ME trajectory {1}")
                        .errorMessageArguments(List.of(sheetName, trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            // Iterate through rows starting from row 2 (skip header at row 1)
            for (Row row : sheet) {
                // Skip header row (row index 0)
                if (row.getRowNum() == 0) {
                    continue;
                }

                Cell cellA = row.getCell(0);
                Cell cellB = row.getCell(1);
                Cell cellC = row.getCell(2);
                Cell cellD = row.getCell(3);
                Cell cellE = row.getCell(4);
                Cell cellF = row.getCell(5);

                // Skip completely empty rows (all cells A-F are null or empty)
                if (isRowEmpty(cellA) && isRowEmpty(cellB) && isRowEmpty(cellC) && 
                    isRowEmpty(cellD) && isRowEmpty(cellE) && isRowEmpty(cellF)) {
                    continue;
                }

                // Validate nodeFrom (Column A) - must not be null/empty
                validateNodeFromColumn(cellA, trajectoryName);

                // Validate nodeTo (Column B) - must not be null/empty
                validateNodeToColumn(cellB, trajectoryName);

                // Validate Direct_MW (Column C) - must be numeric or 'infinite'
                validateDirectMwColumn(cellC, trajectoryName);

                // Validate Indirect_MW (Column D) - must be numeric or 'infinite'
                validateIndirectMwColumn(cellD, trajectoryName);

                // Validate Hurdle Costs Direct (Column E) - must be numeric
                validateHurdleCostsDirectColumn(cellE, trajectoryName);

                // Validate Hurdle Costs Indirect (Column F) - must be numeric
                validateHurdleCostsIndirectColumn(cellF, trajectoryName);

                // Build LinkMeEntity from validated row data
                LinkMeEntity linkMe = LinkMeEntity.builder()
                        .nodeFrom(getCellStringValue(cellA))
                        .nodeTo(getCellStringValue(cellB))
                        .directMw(getNumericCellValue(cellC))
                        .indirectMw(getNumericCellValue(cellD))
                        .hurdleCostsDirect(getNumericCellValue(cellE))
                        .hurdleCostsIndirect(getNumericCellValue(cellF))
                        .build();

                linkMeEntities.add(linkMe);
            }

        } catch (IOException e) {
            throw TechnicalException.builder()
                    .message("Could not read LINKS_ME trajectory file: {0}")
                    .errorMessageArguments(List.of(path.getFileName().toString()))
                    .cause(e)
                    .build();
        }

        return linkMeEntities;
    }

    /**
     * Validates nodeFrom column (Column A)
     * - Must not be null if other columns have data
     * - Must not exceed 60 characters
     */
    private void validateNodeFromColumn(Cell cell, String trajectoryName) {
        String value = getCellStringValue(cell);

        if (value == null || value.isEmpty()) {
            throw BusinessException.builder()
                    .message("nodeFrom column must be filled in in LINKS_ME trajectory {0}")
                    .errorMessageArguments(List.of(trajectoryName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        if (value.length() > 60) {
            throw BusinessException.builder()
                    .message("nodeFrom cannot exceed 60 characters in LINKS_ME trajectory {0}")
                    .errorMessageArguments(List.of(trajectoryName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    /**
     * Validates nodeTo column (Column B)
     * - Must not be null if other columns have data
     * - Must not exceed 60 characters
     */
    private void validateNodeToColumn(Cell cell, String trajectoryName) {
        String value = getCellStringValue(cell);

        if (value == null || value.isEmpty()) {
            throw BusinessException.builder()
                    .message("nodeTo column must be filled in in LINKS_ME trajectory {0}")
                    .errorMessageArguments(List.of(trajectoryName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        if (value.length() > 60) {
            throw BusinessException.builder()
                    .message("nodeTo cannot exceed 60 characters in LINKS_ME trajectory {0}")
                    .errorMessageArguments(List.of(trajectoryName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    /**
     * Validates Direct_MW column (Column C)
     * - Must be numeric or 'infinite'
     */
    private void validateDirectMwColumn(Cell cell, String trajectoryName) {
        if (cell == null || isCellEmpty(cell)) {
            return;
        }

        String value = getCellStringValue(cell);
        if (value != null && !value.isEmpty()) {
            if (!isNumericOrInfinite(value)) {
                throw BusinessException.builder()
                        .message("Column Direct_MW must be numeric or 'infinite' in LINKS_ME trajectory {0}")
                        .errorMessageArguments(List.of(trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
        }
    }

    /**
     * Validates Indirect_MW column (Column D)
     * - Must be numeric or 'infinite'
     */
    private void validateIndirectMwColumn(Cell cell, String trajectoryName) {
        if (cell == null || isCellEmpty(cell)) {
            return;
        }

        String value = getCellStringValue(cell);
        if (value != null && !value.isEmpty()) {
            if (!isNumericOrInfinite(value)) {
                throw BusinessException.builder()
                        .message("Column Indirect_MW must be numeric or 'infinite' in LINKS_ME trajectory {0}")
                        .errorMessageArguments(List.of(trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
        }
    }

    /**
     * Validates Hurdle Costs Direct column (Column E)
     * - Must be numeric
     */
    private void validateHurdleCostsDirectColumn(Cell cell, String trajectoryName) {
        if (cell == null || isCellEmpty(cell)) {
            return;
        }

        if (!isNumericCell(cell)) {
            String value = getCellStringValue(cell);
            if (value != null && !value.isEmpty() && !isNumeric(value)) {
                throw BusinessException.builder()
                        .message("Column Hurdle Costs Direct must be numeric in LINKS_ME trajectory {0}")
                        .errorMessageArguments(List.of(trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
        }
    }

    /**
     * Validates Hurdle Costs Indirect column (Column F)
     * - Must be numeric
     */
    private void validateHurdleCostsIndirectColumn(Cell cell, String trajectoryName) {
        if (cell == null || isCellEmpty(cell)) {
            return;
        }

        if (!isNumericCell(cell)) {
            String value = getCellStringValue(cell);
            if (value != null && !value.isEmpty() && !isNumeric(value)) {
                throw BusinessException.builder()
                        .message("Column Hurdle Costs Indirect must be numeric in LINKS_ME trajectory {0}")
                        .errorMessageArguments(List.of(trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
        }
    }

    /**
     * Helper: Check if value is numeric or 'infinite'
     */
    private boolean isNumericOrInfinite(String value) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        if ("infinite".equalsIgnoreCase(value.trim())) {
            return true;
        }
        return isNumeric(value);
    }

    /**
     * Helper: Check if value is numeric
     */
    private boolean isNumeric(String value) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        try {
            Double.parseDouble(value.replace(",", "."));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Helper: Check if cell is numeric
     */
    private boolean isNumericCell(Cell cell) {
        if (cell == null) {
            return false;
        }
        return cell.getCellType() == CellType.NUMERIC;
    }

    /**
     * Rule 8: Create trajectory entity with metadata
     */
    private TrajectoryEntity createTrajectoryEntity(String trajectoryName, String horizon, String createdBy, String checksum) {
        Optional<TrajectoryEntity> existingTrajectory = trajectoryRepository
                .findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                        trajectoryName, horizon, TrajectoryType.LINK_ME.name()
                );

        int version = 1;
        if (existingTrajectory.isPresent()) {
            version = existingTrajectory.get().getVersion() + 1;
        }

        return TrajectoryEntity.builder()
                .fileName(trajectoryName)
                .horizon(horizon)
                .type(TrajectoryType.LINK_ME.name())
                .checksum(checksum)
                .version(version)
                .createdBy(createdBy)
                .creationDate(LocalDateTime.now())
                .build();
    }

    /**
     * Rule 8: Save LINK_ME entities to database
     */
    private void saveLinkMeEntities(TrajectoryEntity trajectory, List<LinkMeEntity> linkMeEntities) {
        // Link each entity to the trajectory
        linkMeEntities.forEach(linkMe -> linkMe.setTrajectory(trajectory));

        // Save all entities
        linkMeRepository.saveAll(linkMeEntities);
    }

    /**
     * Helper: Get cell value as string
     */
    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return null;
        }

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            case BLANK -> "";
            default -> null;
        };
    }

    /**
     * Helper: Get numeric cell value
     */
    private Double getNumericCellValue(Cell cell) {
        if (cell == null || isCellEmpty(cell)) {
            return null;
        }

        return switch (cell.getCellType()) {
            case NUMERIC -> cell.getNumericCellValue();
            case STRING -> {
                String value = cell.getStringCellValue().trim();
                if (value.isEmpty() || "infinite".equalsIgnoreCase(value)) {
                    yield null;
                }
                try {
                    yield Double.parseDouble(value.replace(",", "."));
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            default -> null;
        };
    }

    /**
     * Helper: Check if cell is empty
     */
    private boolean isCellEmpty(Cell cell) {
        if (cell == null) {
            return true;
        }
        return cell.getCellType() == CellType.BLANK ||
               (cell.getCellType() == CellType.STRING && cell.getStringCellValue().isEmpty());
    }

    /**
     * Helper: Check if a cell is null or empty (for row skip logic)
     */
    private boolean isRowEmpty(Cell cell) {
        return isCellEmpty(cell);
    }
}
