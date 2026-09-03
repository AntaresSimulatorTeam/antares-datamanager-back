package com.rte_france.antares.datamanager_back.service.area_link.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.AreaConfigRepository;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.AreaConfigEntity;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.area_link.AreaFileProcessorService;
import com.rte_france.antares.datamanager_back.service.user.UserService;
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
import java.util.*;

import static com.rte_france.antares.datamanager_back.dto.TrajectoryType.AREA_ME;
import static com.rte_france.antares.datamanager_back.util.CastCellUtil.castDouble;
import static com.rte_france.antares.datamanager_back.util.Utils.*;

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
    public TrajectoryEntity processAreaFile(Path path, String horizon, TrajectoryType trajectoryType) throws IOException {
        String updatedHorizon = horizon;

        if (AREA_ME.equals(trajectoryType)) {
            String[] parts = horizon.split("-");
            if (parts.length == 2) {
                updatedHorizon = parts[1];
            }
        }
        checkIfHorizonExist(path, updatedHorizon, trajectoryType.name());
        ExcelCommonValidator.checkIfColumnsAreValid(path, ExcelFileType.AREAS, updatedHorizon, trajectoryType.name());
        AreasValidator.validateAreaColumns(path, updatedHorizon);
        String fileName = getFileNameWithoutExtensionAndWithoutPrefix(path.getFileName().toString(), trajectoryType.name());
        Optional<TrajectoryEntity> trajectoryEntity = trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(fileName, horizon, trajectoryType.name());

        String createdBy = Optional.ofNullable(userService.getCurrentUserDetails())
                .map(UserInfoDto::getNni)
                .orElse("UNKNOWN_USER");

        int version = trajectoryEntity.map(TrajectoryEntity::getVersion).orElse(0);
        if (trajectoryEntity.isPresent() && checkTrajectoryVersion(path, trajectoryEntity.get())) {
            version = trajectoryEntity.get().getVersion();
        }

        return saveTrajectory(buildTrajectory(path, version, horizon, createdBy, trajectoryType, null, null, null), buildAreaConfigList(path, updatedHorizon));
    }

    public TrajectoryEntity saveTrajectory(TrajectoryEntity trajectory, List<AreaConfigEntity> areaConfigEntities) {
        trajectory.setAreaConfigEntities(areaConfigEntities);
        trajectory.setType(trajectory.getType());
        areaConfigEntities.forEach(areaConfig -> areaConfig.setTrajectory(trajectory));
        areaConfigRepository.saveAll(areaConfigEntities);
        return trajectoryRepository.save(trajectory);
    }

    private List<AreaConfigEntity> buildAreaConfigList(Path path, String horizon) {

        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheet(horizon);
            List<AreaConfigEntity> areaConfigEntities = new ArrayList<>();
            Row header = sheet.getRow(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0 || isRowEmpty(row)) continue;

                AreaEntity areaEntity = findOrCreateAreaEntity(row);

                var value1 = Objects.requireNonNull(getCellValue(row, 1)).toString().toUpperCase(Locale.ROOT);
                var value2 = castDouble(getCellValue(row,2), header.getCell(2).getStringCellValue(), row.getRowNum());
                var value3 = castDouble(getCellValue(row,3), header.getCell(3).getStringCellValue(), row.getRowNum());


               areaConfigEntities.add(new AreaConfigEntity(value1, value2, value3, areaEntity));
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
        String name = area.getCell(0).getStringCellValue().toUpperCase(Locale.ROOT);

        AreaEntity entity = areaRepository.findAreaByNameIgnoreCase(name).orElseGet(() -> AreaEntity.builder().name(name).build());
        entity.setX(area.getCell(4).getNumericCellValue());
        entity.setY(area.getCell(5).getNumericCellValue());
        entity.setR(area.getCell(6).getNumericCellValue());
        entity.setG(area.getCell(7).getNumericCellValue());
        entity.setB(area.getCell(8).getNumericCellValue());

        return areaRepository.save(entity);
    }
}