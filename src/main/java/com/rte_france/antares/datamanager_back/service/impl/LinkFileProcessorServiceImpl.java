package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.TechnicalAntaresDataMangerException;
import com.rte_france.antares.datamanager_back.repository.LinkRepository;
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
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static com.rte_france.antares.datamanager_back.util.Utils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinkFileProcessorServiceImpl implements LinkFileProcessorService {

    private final LinkRepository linkRepository;
    private final TrajectoryRepository trajectoryRepository;
    private final WarningMessageService warningMessageService;
    private final WarningMessageRepository warningMessageRepository;
    private final UserService userService;

    @ExecutionTime
    @Transactional
    public TrajectoryEntity processLinkFile(Path path, String horizon, Integer studyId) throws IOException {
        Set<WarningMessageEntity> warningMessageEntities = new HashSet<>(); // Nouvelle instance locale

        checkIfHorizonExist(path, horizon);
        ExcelCommonValidator.checkIfColumnsAreValid(path, ExcelFileType.LINKS, horizon);
        LinksValidator.linksDuplicateAndCellsValuesChecks(path, ExcelFileType.LINKS, horizon);
        checkForWarnings(path, horizon, warningMessageEntities);
        log.warn("warningMessages : {}", warningMessageEntities);

        Optional<TrajectoryEntity> trajectoryEntity = trajectoryRepository.findFirstByFileNameOrderByVersionDesc(
                getFileNameWithoutExtension(path.getFileName().toString())
        );
        String createdBy = userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : "UNKNOWN_USER";

        if (trajectoryEntity.isPresent() && checkTrajectoryVersion(path, trajectoryEntity.get())) {
            return saveTrajectory(
                    buildTrajectory(path, trajectoryEntity.get().getVersion(), horizon, createdBy),
                    buildLinkList(path, horizon, studyId, warningMessageEntities),
                    warningMessageEntities
            );
        }
        return saveTrajectory(
                buildTrajectory(path, 0, horizon, createdBy),
                buildLinkList(path, horizon, studyId, warningMessageEntities),
                warningMessageEntities
        );
    }

    private void checkForWarnings(Path path, String horizon, Set<WarningMessageEntity> warningMessages) {
        if (LinksValidator.checkPowerColumnsForZeroValues(path, horizon)) {
            buildWarningMessage(warningMessages, WarningCode.LINKS_ALL_VALUES_ZERO, WarningLevel.WARNING_LEVEL);
        }
        if (LinksValidator.areAllValuesZeroInGroup(path, horizon, LinksColumns.getDirectColumnNames())) {
            buildWarningMessage(warningMessages, WarningCode.LINKS_DIRECT_VALUES_ZERO, WarningLevel.WARNING_LEVEL);
        }
        if (LinksValidator.areAllValuesZeroInGroup(path, horizon, LinksColumns.getIndirectColumnNames())) {
            buildWarningMessage(warningMessages, WarningCode.LINKS_INDIRECT_VALUES_ZERO, WarningLevel.WARNING_LEVEL);
        }
    }

    private void buildWarningMessage(Set<WarningMessageEntity> warningMessages, WarningCode warningCode, WarningLevel warningLevel) {
        var message = WarningMessageEntity.builder()
                .warningContent(warningMessageService.getMessage(warningCode.value()))
                .warningCode(warningCode)
                .warningLevel(warningLevel)
                .build();
        warningMessages.add(message);
    }

    public TrajectoryEntity saveTrajectory(TrajectoryEntity trajectory, List<LinkEntity> linkEntities, Set<WarningMessageEntity> warningMessages) {
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
    private List<LinkEntity> buildLinkList(Path path, String horizon, Integer studyId, Set<WarningMessageEntity> warningMessages) throws IOException {
        List<LinkEntity> linkEntities = new ArrayList<>();
        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet hurdleCostSheet = workbook.getSheetAt(0);
            Sheet sLinksSheet = workbook.getSheet(horizon);
            List<String> areaNames = listArea(studyId);
            for (Row row : sLinksSheet) {
                if (row.getRowNum() != 0 && row.getCell(0) != null && !row.getCell(0).getStringCellValue().isEmpty()) {
                    LinkEntity link = LinkEntity.builder()
                            .name(validateLinkAreas(row.getCell(0).getStringCellValue(), areaNames))
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
                            .hurdleCost(hurdleCostSheet.getRow(1).getCell(1).getNumericCellValue())
                            .build();
                    linkEntities.add(link);

                }
            }
            checkConsistencyLinkAndArea(linkEntities, areaNames, warningMessages);
        } catch (IOException e) {
            throw new IOException("could not build link list : " + e.getMessage());
        }
        return linkEntities;
    }

    private void checkConsistencyLinkAndArea(List<LinkEntity> linkEntities, List<String> areaNames, Set<WarningMessageEntity> warningMessages) {
        // Extraire toutes les zones des LinkEntity dans un Set pour une recherche rapide
        Set<String> linkedAreas = linkEntities.stream()
                .flatMap(link -> Arrays.stream(link.getName().split("-")))
                .collect(Collectors.toSet());

        // Vérifier si chaque zone est présente dans les liens
        for (String area : areaNames) {
            if (!linkedAreas.contains(area)) {
                warningMessages.add(WarningMessageEntity.builder()
                        .warningContent(warningMessageService.getMessage(WarningCode.LINKS_AREA_NOT_PRESENT.value(), area))
                        .warningLevel(WarningLevel.WARNING_LEVEL)
                        .build());
            }
        }
    }


    public String validateLinkAreas(String link, List<String> areaNames) {
        String[] areas = link.split("-");
        if (areas.length != 2) {
            throw new TechnicalAntaresDataMangerException("Error: Link " + link + " in LINKS file is not valid");
        }

        for (String area : areas) {
            if (!areaNames.contains(area)) {
                throw new TechnicalAntaresDataMangerException("Error: Area " + area + " in LINKS file is not present in AREA trajectory");
            }
        }
        return link;
    }

    private List<String> listArea(Integer studyId) {
        return trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA.name(), studyId).stream()
                .flatMap(trajectory -> trajectory.getAreaConfigEntities().stream())
                .map(area -> area.getArea().getName())
                .toList();
    }
}
