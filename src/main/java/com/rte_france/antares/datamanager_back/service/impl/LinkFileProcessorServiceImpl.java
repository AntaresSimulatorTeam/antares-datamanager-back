package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.LinkRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.LinkEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.LinkFileProcessorService;
import com.rte_france.antares.datamanager_back.service.WarningMessageService;
import com.rte_france.antares.datamanager_back.util.ExcelFileValidators.ColumnsEnums.LinksColumns;
import com.rte_france.antares.datamanager_back.util.ExcelFileValidators.ExcelCommonValidator;
import com.rte_france.antares.datamanager_back.util.ExcelFileValidators.LinksValidator;
import com.rte_france.antares.datamanager_back.util.ExecutionTime;
import com.rte_france.antares.datamanager_back.util.ExcelFileValidators.ColumnsEnums.ExcelFileType;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.rte_france.antares.datamanager_back.util.Utils.*;


/**
 * Service class for processing area files.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LinkFileProcessorServiceImpl implements LinkFileProcessorService {

    private final LinkRepository linkRepository;
    private final TrajectoryRepository trajectoryRepository;
    private final WarningMessageService warningMessageService;

    /**
     * Processes the given file.
     * If a trajectory with the same file name exists, it updates the trajectory.
     * Otherwise, it creates a new trajectory.
     *
     * @param path the path to the file to process
     */
    @ExecutionTime
    @Transactional
    public TrajectoryEntity processLinkFile(Path path, String horizon) throws IOException {
        checkIfHorizonExist(path, horizon);
        ExcelCommonValidator.checkIfColumnsAreValid(path, ExcelFileType.LINKS, horizon);
        LinksValidator.linksDuplicateAndCellsValuesChecks(path, ExcelFileType.LINKS, horizon);
       //TODO to add warningMessages to TrajectoryEntity to be be mapped to DTO
        List<String> warningMessages =checkForWarnings(path,horizon);
        Optional<TrajectoryEntity> trajectoryEntity = trajectoryRepository.findFirstByFileNameOrderByVersionDesc(path.getFileName().toString());
        if (trajectoryEntity.isPresent() && checkTrajectoryVersion(path, trajectoryEntity.get())) {
            return saveTrajectory(buildTrajectory(path, trajectoryEntity.get().getVersion(),horizon), buildLinkList(path));
        }
        return saveTrajectory(buildTrajectory(path, 0,horizon), buildLinkList(path));
    }

    private List<String> checkForWarnings(Path path, String horizon) throws IOException {
        List<String> warningMessages = new ArrayList<>();


        if (LinksValidator.checkPowerColumnsForZeroValues(path, horizon)) {
            warningMessages.add(warningMessageService.getMessage("links.all_values_zero"));  // Fetch warning message for Direct columns
        }

        if (LinksValidator.areAllValuesZeroInGroup(path, horizon, LinksColumns.getDirectColumnNames())) {
            warningMessages.add(warningMessageService.getMessage("links.direct_values_zero"));  // Fetch warning message for Direct columns
        }


        if (LinksValidator.areAllValuesZeroInGroup(path, horizon, LinksColumns.getIndirectColumnNames())) {
            warningMessages.add(warningMessageService.getMessage("links.indirect_values_zero"));  // Fetch warning message for Indirect columns
        }

        return warningMessages.isEmpty() ? null : warningMessages;
    }

    public TrajectoryEntity saveTrajectory(TrajectoryEntity trajectory, List<LinkEntity> linkEntities) {
        TrajectoryEntity trajectoryEntity = trajectoryRepository.save(trajectory);
        trajectory.setLinkEntities(linkEntities);
        trajectory.setType(TrajectoryType.LINK.name());
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
    private List<LinkEntity> buildLinkList(Path path) throws IOException {
        List<LinkEntity> linkEntities = new ArrayList<>();
        try (InputStream inputStream = Files.newInputStream(path) ;
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet hurdleCostSheet = workbook.getSheetAt(0);
            Sheet sLinksSheet = workbook.getSheetAt(1);
            for (Row row : sLinksSheet) {
                if (row.getRowNum() != 0 && !row.getCell(0).getStringCellValue().isEmpty()) {
                    LinkEntity link = LinkEntity.builder()
                            .name(row.getCell(0).getStringCellValue())
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
        } catch (IOException e) {
            throw new IOException("could not build link list : " + e.getMessage());
        }
        return linkEntities;
    }
}