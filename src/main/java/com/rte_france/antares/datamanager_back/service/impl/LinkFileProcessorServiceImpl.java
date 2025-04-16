package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.TechnicalAntaresDataMangerException;
import com.rte_france.antares.datamanager_back.repository.LinkRepository;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.WarningMessageRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.LinkFileProcessorService;
import com.rte_france.antares.datamanager_back.service.WarningMessageService;
import com.rte_france.antares.datamanager_back.util.ExecutionTime;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.ExcelCommonValidator;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.LinksValidator;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.ExcelFileType;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.LinksColumns;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
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
    private final WarningMessageService warningMessageService;
    private final WarningMessageRepository warningMessageRepository;
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

        checkIfHorizonExist(path, horizon);
        ExcelCommonValidator.checkIfColumnsAreValid(path, ExcelFileType.LINKS, horizon);
        LinksValidator.linksDuplicateAndCellsValuesChecks(path, ExcelFileType.LINKS, horizon);

        Optional<TrajectoryEntity> trajectoryEntity = trajectoryRepository.findFirstByFileNameAndHorizonOrderByVersionDesc(
                getFileNameWithoutExtensionAndWithoutPrefix(path.getFileName().toString(), TrajectoryType.LINK.name()), horizon
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

        List<String> listLinksAlphabeticalOrder = LinksValidator.checkLinksAlphabeticalOrder(path, horizon, LinksColumns.NAME.getDisplayName(), areasSavedForScenario);

        addWarning(warningMessageEntities, allZeroRows, WarningCode.LINKS_ALL_VALUES_ZERO, studyId, userNni, trajectory);
        addWarning(warningMessageEntities, directUnilateralZeroRows, WarningCode.LINKS_UNILATERAL_VALUES_ZERO, studyId, userNni, trajectory);
        addWarning(warningMessageEntities, indirectUnilateralZeroRows, WarningCode.LINKS_UNILATERAL_VALUES_ZERO, studyId, userNni, trajectory);
        addWarning(warningMessageEntities, listLinksAlphabeticalOrder, WarningCode.AREAS_NOT_ORDERED_ALPHABETICALLY, studyId, userNni, trajectory);
    }


    private void addWarning(Set<WarningMessageEntity> warningMessages,
                            List<String> warnings,
                            WarningCode warningCode, Integer studyId, String userNni, TrajectoryEntity trajectory) {
        StudyEntity study = studyRepository.findById(studyId).orElseThrow();

        for (String warning : warnings) {
            String[] parts = warning.split(",");
            var message = WarningMessageEntity.builder()
                    .warningContent(warningMessageService.getMessage(warningCode.value(), (Object[]) parts))
                    .warningLevel(WarningLevel.WARNING_LEVEL)
                    .secondTrajectory(null)
                    .warningCode(warningCode)
                    .study(study)
                    .trajectory(trajectory)
                    .creationDate(LocalDateTime.now())
                    .createdBy(userNni)
                    .build();
            warningMessages.add(message);
        }
    }

    public TrajectoryEntity saveTrajectory(TrajectoryEntity trajectory, List<LinkEntity> linkEntities, Set<WarningMessageEntity> warningMessages) {
        if (trajectory.getFileName() != null && trajectory.getFileName().length() > LINKS_FILE_NAME_MAX_SIZE) {
            throw new IllegalArgumentException("Trajectory name cannot exceed 40 characters.");
        }

        TrajectoryEntity trajectoryEntity = trajectoryRepository.save(trajectory);
        trajectory.setLinkEntities(linkEntities);
        trajectory.setWarningMessages(warningMessages);
        trajectory.setType(TrajectoryType.LINK.name());

        warningMessages.forEach(warning -> warning.setTrajectory(trajectory));
        warningMessageRepository.saveAll(warningMessages);

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
                            .name(validateLinkAreas(row.getCell(0).getStringCellValue(), listArea))
                            .winterHpDirectMw(row.getCell(1).getNumericCellValue())
                            .winterHpIndirectMw(row.getCell(2).getNumericCellValue())
                            .winterHcDirectMw(row.getCell(2).getNumericCellValue())
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
            throw new IOException("could not build link list : " + e.getMessage());
        }
        return linkEntities;
    }

    private int findCellIndexByHorizon(Sheet sheet, String horizon) {
        Row headerRow = sheet.getRow(0); // Récupère la ligne 0
        if (headerRow == null) {
            throw new IllegalArgumentException("Header row is missing in the sheet.");
        }

        for (Cell cell : headerRow) {
            if (cell.getCellType() == CellType.STRING && horizon.equals(cell.getStringCellValue().trim())) {
                return cell.getColumnIndex(); // Retourne l'index de la colonne
            }
        }

        throw new IllegalArgumentException("Horizon '" + horizon + "' not found in the header row.");
    }

    public void checkConsistencyTrajectoryLinkAndArea(List<LinkEntity> linkEntities, List<String> areaNames, Set<WarningMessageEntity> warningMessages, Integer studyId, Integer trajectoryId, TrajectoryEntity secondTrajectory, String userNni) {
        Set<String> linkedAreas = linkEntities.stream()
                .flatMap(link -> Arrays.stream(link.getName().split("-")))
                .collect(Collectors.toSet());

        StudyEntity study = studyRepository.findById(studyId).orElseThrow();

        areaNames.stream()
                .filter(area -> !linkedAreas.contains(area))
                .forEach(area -> addWarningMessage(warningMessages, area, study, trajectoryId, secondTrajectory, userNni));
    }

    private String findUserNni() {
        return userService.getCurrentUserDetails().getNni();
    }

    private void addWarningMessage(Set<WarningMessageEntity> warningMessages, String area, StudyEntity study, Integer trajectoryId, TrajectoryEntity secondTrajectory, String userNni) {
        String warningContent = warningMessageService.getMessage(WarningCode.LINKS_AREA_NOT_PRESENT.value(), area);
        boolean warningExists = warningMessageRepository.existsByWarningContentAndTrajectoryIdAndStudyId(warningContent, trajectoryId, study.getId());

        if (!warningExists) {
            warningMessages.add(WarningMessageEntity.builder()
                    .warningCode(WarningCode.LINKS_AREA_NOT_PRESENT)
                    .warningContent(warningContent)
                    .warningLevel(WarningLevel.WARNING_LEVEL)
                    .creationDate(LocalDateTime.now())
                    .createdBy(userNni)
                    .study(study)
                    .secondTrajectory(secondTrajectory)
                    .build());
        }
    }


    /**
     * Validates the link areas by checking if the link contains exactly two areas
     * and if both areas are present in the provided list of area names
     * If an area from the list is not present in the link, a warning message is added.
     * This method is case-insensitive
     *
     * @param link      the link to validate
     * @param areaNames the list of valid area names
     * @return the validated link
     * @throws TechnicalAntaresDataMangerException if the link is not valid or an area is not present
     */
    public String validateLinkAreas(String link, List<String> areaNames) {
        String[] areas = link.split("-");
        if (areas.length != 2) {
            throw new TechnicalAntaresDataMangerException("Error: Link " + link + " in LINKS file is not valid");
        }

        for (String area : areas) {
            boolean found = areaNames.stream()
                    .anyMatch(existingArea -> existingArea.equalsIgnoreCase(area));
            if (!found) {
                throw new TechnicalAntaresDataMangerException("Error: Area " + area + " in LINKS file is not present in AREA trajectory");
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