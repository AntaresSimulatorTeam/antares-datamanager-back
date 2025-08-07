package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
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
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.rte_france.antares.datamanager_back.util.Utils.*;
import static com.rte_france.antares.datamanager_back.util.excel_file_validators.ExcelCommonValidator.getBooleanCellValue;

@Slf4j
@Service
@RequiredArgsConstructor
public class AreaFileProcessorServiceImpl implements AreaFileProcessorService {

    private final AreaRepository areaRepository;
    private final AreaConfigRepository areaConfigRepository;
    private final TrajectoryRepository trajectoryRepository;
    private final UserService userService;

    @SuppressWarnings("java:S2083")
    @ExecutionTime
    @Transactional
    public TrajectoryEntity processAreaFile(Path path, String horizon) throws IOException {
        checkIfHorizonExist(path, horizon, TrajectoryType.AREA.name());
        ExcelCommonValidator.checkIfColumnsAreValid(path, ExcelFileType.AREAS, horizon, TrajectoryType.AREA.name());
        AreasValidator.validateAreaColumns(path, horizon);
        String fileName = getFileNameWithoutExtensionAndWithoutPrefix(path.getFileName().toString(), TrajectoryType.AREA.name());
        Optional<TrajectoryEntity> trajectoryEntity = trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(fileName, horizon, TrajectoryType.AREA.name());

        String createdBy = Optional.ofNullable(userService.getCurrentUserDetails())
                .map(UserInfoDto::getNni)
                .orElse("UNKNOWN_USER");

        int version = trajectoryEntity.map(TrajectoryEntity::getVersion).orElse(0);
        if (trajectoryEntity.isPresent() && checkTrajectoryVersion(path, trajectoryEntity.get())) {
            version = trajectoryEntity.get().getVersion();
        }

        return saveTrajectory(buildTrajectory(path, version, horizon, createdBy, TrajectoryType.AREA, null, "CCGD"), buildAreaConfigList(path, horizon));
    }

    public TrajectoryEntity saveTrajectory(TrajectoryEntity trajectory, List<AreaConfigEntity> areaConfigEntities) {
        trajectory.setAreaConfigEntities(areaConfigEntities);
        trajectory.setType(TrajectoryType.AREA.name());
        areaConfigEntities.forEach(areaConfig -> areaConfig.setTrajectory(trajectory));
        areaConfigRepository.saveAll(areaConfigEntities);
        return trajectoryRepository.save(trajectory);
    }

    private List<AreaConfigEntity> buildAreaConfigList(Path path, String horizon) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheet(horizon);
            List<AreaConfigEntity> areaConfigEntities = new ArrayList<>();

            for (Row row : sheet) {
                if (row.getRowNum() == 0 || isRowEmpty(row)) continue;

                AreaEntity areaEntity = findOrCreateAreaEntity(row);
                Boolean value1 = getBooleanCellValue(row.getCell(1));
                Boolean value2 = getBooleanCellValue(row.getCell(2));

                areaConfigEntities.add(new AreaConfigEntity(value1, value2, areaEntity));
            }
            return areaConfigEntities;
        } catch (IOException e) {
            throw TechnicalException.builder().message("could not build area config list: " + e.getMessage()).build();
        }
    }

    private boolean isRowEmpty(Row row) {
        Cell firstCell = row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return firstCell == null || firstCell.getCellType() == CellType.BLANK ||
                (firstCell.getCellType() == CellType.STRING && firstCell.getStringCellValue().trim().isEmpty());
    }

    private AreaEntity findOrCreateAreaEntity(Row area) {
        return areaRepository.findAreaByName(area.getCell(0).getStringCellValue())
                .orElseGet(() -> areaRepository.save(AreaEntity.builder()
                        .name(area.getCell(0).getStringCellValue())
                        .x(area.getCell(3).getNumericCellValue())
                        .y(area.getCell(4).getNumericCellValue())
                        .r(area.getCell(5).getNumericCellValue())
                        .g(area.getCell(6).getNumericCellValue())
                        .b(area.getCell(7).getNumericCellValue())
                        .build()));
    }
}