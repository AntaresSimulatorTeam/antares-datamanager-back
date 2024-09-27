package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.LinkRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.LinkEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.LinkFileProcessorService;
import com.rte_france.antares.datamanager_back.util.ExecutionTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
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

    /**
     * Processes the given file.
     * If a trajectory with the same file name exists, it updates the trajectory.
     * Otherwise, it creates a new trajectory.
     *
     * @param file the file to process
     */
    @ExecutionTime
    @Transactional
    public TrajectoryEntity processLinkFile(File file, String horizon) throws IOException {
        checkIfHorizonExist(file, horizon);

        Optional<TrajectoryEntity> trajectoryEntity = trajectoryRepository.findFirstByFileNameOrderByVersionDesc(file.getName());
        if (trajectoryEntity.isPresent() && checkTrajectoryVersion(file, trajectoryEntity.get())) {
            return saveTrajectory(buildTrajectory(file, trajectoryEntity.get().getVersion(),horizon), buildLinkList(file));
        }
        return saveTrajectory(buildTrajectory(file, 0,horizon), buildLinkList(file));
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
     * @param file the file to process
     * @return a list of area configurations
     */
    private List<LinkEntity> buildLinkList(File file) throws IOException {
        List<LinkEntity> linkEntities = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {

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