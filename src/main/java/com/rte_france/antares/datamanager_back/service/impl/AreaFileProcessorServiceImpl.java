package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.AreaConfigRepository;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.AreaConfigEntity;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.AreaFileProcessorService;
import com.rte_france.antares.datamanager_back.util.ExecutionTime;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.AreasValidator;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.ExcelCommonValidator;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.ExcelFileType;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.rte_france.antares.datamanager_back.util.Utils.*;


/**
 * Service class for processing area files.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AreaFileProcessorServiceImpl implements AreaFileProcessorService {

    private final AreaRepository areaRepository;
    private final AreaConfigRepository areaConfigRepository;
    private final TrajectoryRepository trajectoryRepository;
    private  final UserService userService;

    /**
     * Processes the given file.
     * If a trajectory with the same file name exists, it updates the trajectory.
     * Otherwise, it creates a new trajectory.
     *
     * @param path the path to the file to process
     */
    @ExecutionTime
    @Transactional
    public TrajectoryEntity processAreaFile(Path path, String horizon) throws IOException {
        checkIfHorizonExist(path, horizon);
        //TODO test is not passing if AreasValidator commented and number in AREAS  check if it commes from Excelr or other?
        ExcelCommonValidator.checkIfColumnsAreValid(path, ExcelFileType.AREAS, horizon);
        AreasValidator.validateAreaColumns(path, horizon);
        Optional<TrajectoryEntity> trajectoryEntity = trajectoryRepository.findFirstByFileNameOrderByVersionDesc(getFileNameWithoutExtension(path.getFileName().toString()));
        String createdBy = userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : "UNKNOWEN__USER";
        if (trajectoryEntity.isPresent() && checkTrajectoryVersion(path, trajectoryEntity.get())) {
            return saveTrajectory(buildTrajectory(path, trajectoryEntity.get().getVersion(),horizon, createdBy), buildAreaConfigList(path));
        }
        return saveTrajectory(buildTrajectory(path, 0,horizon, createdBy), buildAreaConfigList(path));
    }



    /**
     * Saves the given trajectory and its associated area configurations.
     *
     * @param trajectory         the trajectory to save
     * @param areaConfigEntities the area configurations to save
     */
    public TrajectoryEntity saveTrajectory(TrajectoryEntity trajectory, List<AreaConfigEntity> areaConfigEntities) {
        TrajectoryEntity trajectoryEntity = trajectoryRepository.save(trajectory);
        trajectory.setAreaConfigEntities(areaConfigEntities);
        trajectory.setType(TrajectoryType.AREA.name());
        areaConfigEntities.forEach(areaConfig -> areaConfig.setTrajectory(trajectory));
        areaConfigRepository.saveAll(areaConfigEntities);
        return trajectoryEntity;
    }

    /**
     * Builds a list of area configurations from the given file.
     *
     * @param path the path to the file to process
     * @return a list of area configurations
     */
    private List<AreaConfigEntity> buildAreaConfigList(Path path) throws IOException {
        List<AreaConfigEntity> areaConfigEntities = new ArrayList<>();
        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

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
            throw new IOException("could not build area config list : " + e.getMessage());
        }
        return areaConfigEntities;
    }

    /**
     * Finds an existing area entity by name or creates a new one if it doesn't exist.
     *
     * @param area the row representing the area
     * @return the found or created area entity
     */
    private AreaEntity findOrCreateAreaEntity(Row area) {
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