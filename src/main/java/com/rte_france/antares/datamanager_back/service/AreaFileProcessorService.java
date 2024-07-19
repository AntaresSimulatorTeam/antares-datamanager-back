package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.Type;
import com.rte_france.antares.datamanager_back.repository.AreaConfigRepository;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.AreaConfigEntity;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.rte_france.antares.datamanager_back.util.Utils.*;


/**
 * Service class for processing area files.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AreaFileProcessorService {

    private final AreaRepository areaRepository;
    private final AreaConfigRepository areaConfigRepository;
    private final TrajectoryRepository trajectoryRepository;

    /**
     * Processes the given file.
     * If a trajectory with the same file name exists, it updates the trajectory.
     * Otherwise, it creates a new trajectory.
     *
     * @param file the file to process
     */
@Transactional
public void processAreaFile(File file) {
    var startProcessing = LocalDateTime.now();
    log.info("START processing file : " + file.getName() + " at " + LocalDateTime.now());
    trajectoryRepository.findFirstByFileNameOrderByVersionDesc(file.getName()).ifPresentOrElse(
            trajectoryEntity -> processTrajectory(file, trajectoryEntity),
            () -> processNewTrajectory(file)
    );
    var endProcessing = LocalDateTime.now();
    log.info("Time spend to process area file : " + file.getName() + " is " + endProcessing.minusNanos(startProcessing.getNano()).getNano() + " nanoSeconds");
}

private void processTrajectory(File file, TrajectoryEntity trajectoryEntity) {
    try {
      if(checkTrajectoryVersion(file, trajectoryEntity)) {
          saveTrajectoryWithListOfAreaConfig(buildTrajectory(file, trajectoryEntity), buildAreaConfigList(file));
      }
    } catch (IOException e) {
        throw new RuntimeException(e);
    }
}

private void processNewTrajectory(File file) {
    try {
        saveTrajectoryWithListOfAreaConfig(buildTrajectory(file, null), buildAreaConfigList(file));
    } catch (IOException e) {
        throw new RuntimeException(e);
    }
}

    /**
     * Saves the given trajectory and its associated area configurations.
     *
     * @param trajectory         the trajectory to save
     * @param areaConfigEntities the area configurations to save
     */
    public void saveTrajectoryWithListOfAreaConfig(TrajectoryEntity trajectory, List<AreaConfigEntity> areaConfigEntities) {
        trajectoryRepository.save(trajectory);
        trajectory.setAreaConfigEntities(areaConfigEntities);
        trajectory.setType(Type.AREA.name());
        areaConfigEntities.forEach(areaConfig -> areaConfig.setTrajectory(trajectory));
        areaConfigRepository.saveAll(areaConfigEntities);
    }

    /**
     * Builds a list of area configurations from the given file.
     *
     * @param file the file to process
     * @return a list of area configurations
     */
    private List<AreaConfigEntity> buildAreaConfigList(File file) {
        List<AreaConfigEntity> areaConfigEntities = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() != 0 && !row.getCell(0).getStringCellValue().isEmpty()) {
                    AreaEntity areaEntity = findOrCreateAreaEntity(row);
                    AreaConfigEntity areaConfigEntity = new AreaConfigEntity(
                            Boolean.valueOf(row.getCell(1).getStringCellValue()),
                            Boolean.valueOf(row.getCell(2).getStringCellValue()),
                            areaEntity);
                    areaConfigEntities.add(areaConfigEntity);

                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return areaConfigEntities;
    }

    /**
     * Finds an existing area entity by name or creates a new one if it doesn't exist.
     *
     * @param area the row representing the area
     * @return the found or created area entity
     */
    public AreaEntity findOrCreateAreaEntity(Row area) {
        return areaRepository.findAreaByName(area.getCell(0).getStringCellValue()).orElseGet(() -> {
            AreaEntity areaEntity = AreaEntity.builder()
                    .name(area.getCell(0).getStringCellValue())
                    .x(area.getCell(3).getNumericCellValue())
                    .y(area.getCell(4).getNumericCellValue())
                    .r(area.getCell(5).getNumericCellValue())
                    .g(area.getCell(6).getNumericCellValue())
                    .b(area.getCell(7).getNumericCellValue())
                    .build();
            return areaRepository.save(areaEntity);

        });
    }
}