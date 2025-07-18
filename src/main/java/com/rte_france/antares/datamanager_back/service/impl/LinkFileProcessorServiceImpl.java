package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.LinkRepository;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.WarningRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.LinkFileProcessorService;
import com.rte_france.antares.datamanager_back.service.WarningService;
import com.rte_france.antares.datamanager_back.util.ExecutionTime;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.ExcelCommonValidator;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.LinksValidator;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.ExcelFileType;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.LinksColumns;
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
import java.util.stream.Collectors;

import static com.rte_france.antares.datamanager_back.util.Utils.*;


/**
 * Service class for processing area files.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LinkFileProcessorServiceImpl implements LinkFileProcessorService {
    private final StudyRepository studyRepository;
    private static final int LINKS_FILE_NAME_MAX_SIZE = 40;
    private final LinkRepository linkRepository;
    private final TrajectoryRepository trajectoryRepository;
    private final WarningService warningService;
    private final WarningRepository warningRepository;
    private final UserService userService;

    /**
     * Processes the given file.
     * If a trajectory with the same file name exists, it updates the trajectory.
     * Otherwise, it creates a new trajectory.
     * It checks also the rules for errors and warnings
     *
     * @param path the path to the file to process
     */
    @ExecutionTime
    @Transactional
    public TrajectoryEntity processLinkFile(Path path, String horizon, Integer studyId) throws IOException {
        Set<WarningMessageEntity> warningMessageEntities = new HashSet<>(); // Nouvelle instance locale

        checkIfHorizonExist(path, horizon, TrajectoryType.LINK.name());
        ExcelCommonValidator.checkIfColumnsAreValid(path, ExcelFileType.LINKS, horizon, TrajectoryType.LINK.name());
        LinksValidator.linksDuplicateAndCellsValuesChecks(path, ExcelFileType.LINKS, horizon);

        Optional<TrajectoryEntity> trajectoryEntity = trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                getFileNameWithoutExtensionAndWithoutPrefix(path.getFileName().toString(), TrajectoryType.LINK.name()), horizon, TrajectoryType.LINK.name()
        );
        String createdBy = userService.getCurrentUserDetails().getNni();

        List<String> areaNames = findListArea(studyId);

        List<LinkEntity> listLink = buildLinkList(path, horizon, areaNames);

        TrajectoryEntity trajectory;
        if (trajectoryEntity.isPresent() && checkTrajectoryVersion(path, trajectoryEntity.get())) {
            trajectory = buildTrajectory(path, trajectoryEntity.get().getVersion(), horizon, createdBy, TrajectoryType.LINK);
        } else {
            trajectory = buildTrajectory(path, 0, horizon, createdBy, TrajectoryType.LINK);
        }

        TrajectoryEntity secondTrajectory = trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA.name(), studyId).stream().findFirst().orElse(null);
        String userNni = findUserNni();

        checkForWarnings(path, horizon, studyId, warningMessageEntities, userNni, trajectory);
        checkConsistencyTrajectoryLinkAndArea(listLink, areaNames, warningMessageEntities, studyId, trajectory.getId(), secondTrajectory, userNni);

        return saveTrajectory(trajectory, listLink, warningMessageEntities);
    }

    /**
     * Check for warnings Isolated Zone, Unilateral link and Alphabetical order
     * When all values are zero (across both direct and indirect columns) only Isolated Zone
     * warning should be raised
     */
    private void checkForWarnings(Path path, String horizon, Integer studyId, Set<WarningMessageEntity> warningMessageEntities, String userNni, TrajectoryEntity trajectory) {
        List<String> areasSavedForScenario = findListArea(studyId);

        List<String> allZeroRows = LinksValidator.checkPowerColumnsForZeroValues(path, horizon);

        List<String> directColumns = LinksColumns.getDirectColumnNames();
        List<String> directUnilateralZeroRows = LinksValidator.areAllValuesZeroInGroup(path, horizon, directColumns);
        directUnilateralZeroRows.removeAll(allZeroRows); // Exclude all-zero rows

        List<String> indirectColumns = LinksColumns.getIndirectColumnNames();
        List<String> indirectUnilateralZeroRows = LinksValidator.areAllValuesZeroInGroup(path, horizon, indirectColumns);
        indirectUnilateralZeroRows.removeAll(allZeroRows); // Exclude all-zero rows

        List<String> allUnilateralZeroRows = new ArrayList<>();
        allUnilateralZeroRows.addAll(directUnilateralZeroRows);
        allUnilateralZeroRows.addAll(indirectUnilateralZeroRows);


        List<String> listLinksAlphabeticalOrder = LinksValidator.checkLinksAlphabeticalOrder(path, horizon, LinksColumns.NAME.getDisplayName(), areasSavedForScenario);

        addWarning(warningMessageEntities, allZeroRows, WarningCode.LINKS_ALL_VALUES_ZERO, studyId, userNni, trajectory);
        addWarning(warningMessageEntities, allUnilateralZeroRows, WarningCode.LINKS_UNILATERAL_VALUES_ZERO, studyId, userNni, trajectory);
        addWarning(warningMessageEntities, listLinksAlphabeticalOrder, WarningCode.AREAS_NOT_ORDERED_ALPHABETICALLY, studyId, userNni, trajectory);
    }


    private void addWarning(Set<WarningMessageEntity> warningMessages,
                            List<String> warnings,
                            WarningCode warningCode,
                            Integer studyId,
                            String userNni,
                            TrajectoryEntity trajectory) {
        if (warnings.isEmpty()) {
            return;
        }

        String warningContent = String.join(", ", warnings);

        StudyEntity study = studyRepository.findById(studyId).orElseThrow();

        var message = WarningMessageEntity.builder()
                .warningContent(warningService.getMessage(warningCode.value(), warningContent))
                .warningLevel(WarningLevel.WARNING_LEVEL)
                .secondTrajectory(null)
                .warningCode(warningCode)
                .study(study)
                .trajectory(trajectory)
                .creationDate(LocalDateTime.now())
                .createdBy(userNni)
                .isAck(false)
                .build();

        warningMessages.add(message);
    }


    public TrajectoryEntity saveTrajectory(TrajectoryEntity trajectory, List<LinkEntity> linkEntities, Set<WarningMessageEntity> warningMessages) {
        if (trajectory.getFileName() != null && trajectory.getFileName().length() > LINKS_FILE_NAME_MAX_SIZE) {
            throw BusinessException.builder().message("Trajectory name cannot exceed 40 characters.").build();
        }

        TrajectoryEntity trajectoryEntity = trajectoryRepository.save(trajectory);
        trajectory.setLinkEntities(linkEntities);
        trajectory.setWarningMessages(warningMessages);
        trajectory.setType(TrajectoryType.LINK.name());

        warningMessages.forEach(warning -> warning.setTrajectory(trajectory));
        warningRepository.saveAll(warningMessages);

        linkEntities.forEach(link -> link.setTrajectory(trajectory));
        linkRepository.saveAll(linkEntities);

        return trajectoryEntity;
    }

    /**
     * Builds a list of area configurations from the given file.
     *
     * @param path the path to the file to process
     * @return a list of area configurations
     */
    private List<LinkEntity> buildLinkList(Path path, String horizon, List<String> listArea) throws IOException {
        List<LinkEntity> linkEntities = new ArrayList<>();
        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet hurdleCostSheet = workbook.getSheet("parameters");
            Sheet sLinksSheet = workbook.getSheet(horizon);
            for (Row row : sLinksSheet) {
                if (row.getRowNum() != 0 && row.getCell(0) != null && !row.getCell(0).getStringCellValue().isEmpty()) {
                    LinkEntity link = LinkEntity.builder()
                            .name(row.getCell(0).getStringCellValue())
                            .winterHpDirectMw(row.getCell(1).getNumericCellValue())
                            .winterHpIndirectMw(row.getCell(2).getNumericCellValue())
                            .winterHcDirectMw(row.getCell(3).getNumericCellValue())
                            .winterHcIndirectMw(row.getCell(4).getNumericCellValue())
                            .summerHpDirectMw(row.getCell(5).getNumericCellValue())
                            .summerHpIndirectMw(row.getCell(6).getNumericCellValue())
                            .summerHcDirectMw(row.getCell(7).getNumericCellValue())
                            .summerHcIndirectMw(row.getCell(8).getNumericCellValue())
                            .flowbasedPerimeter(Boolean.valueOf(row.getCell(9).getStringCellValue()))
                            .hvdc(Boolean.valueOf(row.getCell(10).getStringCellValue()))
                            .specificTs(Boolean.valueOf(row.getCell(11).getStringCellValue()))
                            .forcedOutageHvac(Boolean.valueOf(row.getCell(12).getStringCellValue()))
                            .hurdleCost(hurdleCostSheet.getRow(1).getCell(findCellIndexByHorizon(hurdleCostSheet, horizon)).getNumericCellValue())
                            .build();
                    linkEntities.add(link);

                }
            }
        } catch (IOException e) {
            throw TechnicalException.builder().message("could not build link list : " + e.getMessage()).build();
        }
        return linkEntities;
    }

    private int findCellIndexByHorizon(Sheet sheet, String horizon) {
        Row headerRow = sheet.getRow(0); // Récupère la ligne 0
        if (headerRow == null) {
            throw TechnicalException.builder().message("Header row is missing in the sheet.").build();
        }

        for (Cell cell : headerRow) {
            if (cell.getCellType() == CellType.STRING && horizon.equals(cell.getStringCellValue().trim())) {
                return cell.getColumnIndex(); // Retourne l'index de la colonne
            }
        }

        throw BusinessException.builder()
                .message("Horizon {0} not found in the header row.")
                .errorMessageArguments(Collections.singletonList(horizon))
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build();
    }

    public void checkConsistencyTrajectoryLinkAndArea(List<LinkEntity> linkEntities, List<String> areaNames, Set<WarningMessageEntity> warningMessages, Integer studyId, Integer trajectoryId, TrajectoryEntity secondTrajectory, String userNni) {
        log.info("areaNames from AREA trajectory: {}", areaNames);
        Set<String> linkedAreas = linkEntities.stream()
                .flatMap(link -> Arrays.stream(link.getName().split("-")))
                .collect(Collectors.toSet());
        log.info("Linked areas from LINKS file: {}", linkedAreas);

        StudyEntity study = studyRepository.findById(studyId).orElseThrow();
        Set<String> missingAreas = areaNames.stream()
                .filter(area -> !linkedAreas.contains(area))
                .collect(Collectors.toSet());

        if (!missingAreas.isEmpty()) {
            log.info("Missing areas in LINKS file: {}", missingAreas);
            String missingAreasString = String.join(", ", missingAreas.stream().sorted().toList());
            String warningContent = warningService.getMessage(WarningCode.LINKS_AREA_NOT_PRESENT.value(), missingAreasString);
            log.warn("Warning: {}", warningContent);
            boolean warningExists = warningRepository.existsByWarningContentAndTrajectoryIdAndStudyId(warningContent, trajectoryId, study.getId());
            if (!warningExists) {
                warningMessages.add(WarningMessageEntity.builder()
                        .warningCode(WarningCode.LINKS_AREA_NOT_PRESENT)
                        .warningContent(warningContent)
                        .warningLevel(WarningLevel.WARNING_LEVEL)
                        .creationDate(LocalDateTime.now())
                        .createdBy(userNni)
                        .study(study)
                        .secondTrajectory(secondTrajectory)
                        .isAck(false)
                        .build());
            }
        }
    }


    private String findUserNni() {
        return userService.getCurrentUserDetails().getNni();
    }

    /**
     * Validates the link areas by checking if the link contains exactly two areas
     * and if both areas are present in the provided list of area names
     * If an area from the list is not present in the link, an error is throw
     * This method is case-insensitive
     *
     * @param link      the link to validate
     * @param areaNames the list of valid area names
     * @return the validated link
     * @throws BusinessException if the link is not valid or an area is not present
     */
    public String validateLinkAreas(String link, List<String> areaNames) {
        String[] areas = link.split("-");
        if (areas.length != 2) {
            throw BusinessException.builder()
                    .message("Error: Link {0} in LINKS file is not valid")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .errorMessageArguments(List.of(link))
                    .build();
        }

        for (String area : areas) {
            boolean found = areaNames.stream()
                    .anyMatch(existingArea -> existingArea.equalsIgnoreCase(area));
            if (!found) {
                throw BusinessException.builder()
                        .message("Areas {0} in LINKS file is not present in AREA trajectory")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .errorMessageArguments(List.of(area))
                        .build();
            }
        }

        return link;
    }

    /**
     * Method to fetch areas already associated to study in database
     */
    public List<String> findListArea(Integer studyId) {
        return trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA.name(), studyId).stream()
                .flatMap(trajectory -> trajectory.getAreaConfigEntities().stream())
                .map(area -> area.getArea().getName())
                .toList();
    }

    /**
     * Method to fetch areas already associated to study in database
     */
    public List<LinkEntity> findListLink(Integer studyId) {
        return trajectoryRepository.findByTypeAndStudyId(TrajectoryType.LINK.name(), studyId).stream()
                .flatMap(trajectory -> trajectory.getLinkEntities().stream())
                .toList();
    }

}