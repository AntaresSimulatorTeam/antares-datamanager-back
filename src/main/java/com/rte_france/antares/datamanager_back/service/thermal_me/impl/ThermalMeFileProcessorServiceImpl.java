package com.rte_france.antares.datamanager_back.service.thermal_me.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.ThermalMeEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.thermal_me.ThermalMeFileProcessorService;
import com.rte_france.antares.datamanager_back.service.user.UserService;
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
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.rte_france.antares.datamanager_back.util.Utils.*;
import static com.rte_france.antares.datamanager_back.util.excel_file_validators.ExcelCommonValidator.isRowEmpty;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThermalMeFileProcessorServiceImpl implements ThermalMeFileProcessorService {

    private final TrajectoryRepository trajectoryRepository;
    private final UserService userService;
    private final AntaresDataManagerProperties antaresDataManagerProperties;
    
    private static final String THERMAL_ME = "THERMAL_ME";
    private static final String MARGINAL_COST_MODULATION = "marginal_cost_modulation";
    private static final String MARKET_BID_MODULATION = "market_bid_modulation";
    private static final String MUST_RUN = "must_run";
    private static final String CAPACITY_MODULATION = "capacity_modulation";
    
    private static final List<String> TIMESTEP_VALUES = List.of("hourly", "annual");
    private static final Map<Integer, String> MODULATION_FOLDERS = Map.of(7, MARGINAL_COST_MODULATION, 10, MARKET_BID_MODULATION, 13, MUST_RUN, 15, CAPACITY_MODULATION); 
    
    enum CheckValueType {
        BOOLEAN,
        NUMERIC,
        TIMESTEP,
        LENGTH
    }
    public record CheckValueDefinition<T>(
            CheckValueType type,
            T value
    ) {
    }
    private static final Map<Integer, CheckValueDefinition<?>> CHECK_VALUE_LIST = new LinkedHashMap<>();

    static {
        Set<Integer> lengthValues = Set.of(0,1,2);
        Set<Integer> booleanValues = Set.of(3,12);
        Set<Integer> timestepValues = Set.of(7, 10, 13, 15);

        for (int i = 0; i <= 16; i++) {
            CHECK_VALUE_LIST.put(
                    i,
                    booleanValues.contains(i)
                            ? new CheckValueDefinition<>(CheckValueType.BOOLEAN, null)
                            : timestepValues.contains(i)
                            ? new CheckValueDefinition<>(CheckValueType.TIMESTEP, MODULATION_FOLDERS.get(i))
                            : lengthValues.contains(i)
                            ? new CheckValueDefinition<>(CheckValueType.LENGTH, i == 0 ? 60 : i == 1 ? 20 : 40)
                            : new CheckValueDefinition<>(CheckValueType.NUMERIC, null)
            );
        }
    }

    @Transactional
    @Override
    public TrajectoryEntity processThermalMeFile(String trajectoryToUse, String horizon, Integer studyId) throws IOException {
        return saveThermalMeTrajectoryInDb(trajectoryToUse, horizon);
    }

    public TrajectoryEntity saveThermalMeTrajectoryInDb(String trajectoryToUse, String horizon) throws IOException {
        if (horizon == null) {
            throw BusinessException.builder()
                    .message("Horizon must not be null")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        if (trajectoryToUse.length() > 40) {
            throw BusinessException.builder()
                    .message("Trajectory name cannot exceed 40 characters")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        String userNni = Optional.ofNullable(userService.getCurrentUserDetails())
                .map(UserInfoDto::getNni)
                .orElseThrow(() ->
                        BusinessException.builder()
                                .message("User NNI could not be determined")
                                .httpStatus(HttpStatus.BAD_REQUEST)
                                .build());

        String nasDir = antaresDataManagerProperties.getNasDirectory();
        String trajFilePath = antaresDataManagerProperties.getTrajectoryFilePath();
        String directoryByType = antaresDataManagerProperties.getThermalMeDirectory();
        Path trajectoryPath = buildTrajectoryPath(nasDir, trajFilePath, directoryByType, trajectoryToUse);

        try (InputStream inputStream = Files.newInputStream(trajectoryPath); 
            Workbook workbook = WorkbookFactory.create(inputStream)) {

            String horizonYear = String.valueOf(Integer.parseInt(horizon.split("-")[1]));
            Sheet sheet = workbook.getSheet(horizonYear);
            if (sheet == null || isSheetEmpty(sheet)) {
                throw BusinessException.builder()
                        .message("Missing horizon {0} in {1} trajectory {2}")
                        .errorMessageArguments(List.of(horizonYear, THERMAL_ME, trajectoryToUse))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            Optional<TrajectoryEntity> existingTrajectoryOpt = trajectoryRepository
                    .findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(trajectoryToUse, horizon, TrajectoryType.THERMAL_CAPACITY_ME.name());

            if (existingTrajectoryOpt.isPresent()) {
                TrajectoryEntity existingTrajectory = existingTrajectoryOpt.get();
                if (isSameFileWithSameContent(trajectoryPath, existingTrajectory)) {
                    throw BusinessException.builder()
                            .message("File already processed with same content : {0}")
                            .errorMessageArguments(List.of(trajectoryToUse))
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build();
                }
                TrajectoryEntity newTrajectory = buildNewTrajectory(TrajectoryType.THERMAL_CAPACITY_ME, trajectoryToUse, horizon, trajectoryPath, userNni);
                newTrajectory.setVersion(existingTrajectory.getVersion() + 1);
                return trajectoryRepository.save(newTrajectory);
            }
            TrajectoryEntity newTrajectory = buildNewTrajectory(TrajectoryType.THERMAL_CAPACITY_ME, trajectoryToUse, horizon, trajectoryPath, userNni);
            Path baseDirectory = Path.of(nasDir)
                    .resolve(trajFilePath)
                    .resolve(directoryByType);
            buildEntities(sheet, newTrajectory, trajectoryToUse, baseDirectory);
            return  trajectoryRepository.save(newTrajectory);

        } catch (IOException e) {
            throw TechnicalException.builder()
                    .message("Could not process THERMAL ME file: " + e.getMessage())
                    .cause(e)
                    .build();
        }
    }

    private List<ThermalMeEntity> buildEntities(Sheet sheet, TrajectoryEntity trajectory, String trajectoryToUse, Path trajectoryPath) {
        List<ThermalMeEntity> thermalMeEntities = new ArrayList<>();
        List<String> columnNames = new ArrayList<>();
        
        for (Row row : sheet) {
            if (row == null || isRowEmpty(row) || row.getRowNum() < 3) continue;
            if (row.getRowNum() == 3) {
                for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                    columnNames.add(Objects.toString(getCellValue(row, r)));   
                }
                continue;
            }
            
            if (getCellValue(row, 0) == null) {
                throw BusinessException.builder()
                        .message("{0} column must be filled in {1} trajectory {2}")
                        .errorMessageArguments(List.of(columnNames.get(0), THERMAL_ME, trajectoryToUse))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            for (Map.Entry<Integer, CheckValueDefinition<?>> entry : CHECK_VALUE_LIST.entrySet()) {
                int columnIndex = entry.getKey();
                Cell cell = row.getCell(columnIndex);
                CheckValueType type = entry.getValue().type;
                
                switch (type) {
                    case CheckValueType.LENGTH:
                        String value = Objects.toString(getCellValue(row, columnIndex));
                        int nbCharactersMax = (int) entry.getValue().value;
                        if (value != null && value.length() > nbCharactersMax) {
                            throw BusinessException.builder()
                                    .message("{0} cannot exceed {1} characters in {2} trajectory {3}")
                                    .errorMessageArguments(List.of(columnNames.get(columnIndex), Integer.toString(nbCharactersMax), THERMAL_ME, trajectoryToUse))
                                    .httpStatus(HttpStatus.BAD_REQUEST)
                                    .build();
                        }
                        break;
                    case CheckValueType.BOOLEAN:
                        if (!isBooleanCell(cell)) {
                            throw BusinessException.builder()
                                    .errorMessageArguments(List.of(columnNames.get(columnIndex), THERMAL_ME, trajectoryToUse))
                                    .message("{0} must be boolean in {1} trajectory {2}")
                                    .httpStatus(HttpStatus.BAD_REQUEST)
                                    .build();
                        }
                        break;

                    case CheckValueType.TIMESTEP:
                        String valueTimeStep = cell.getStringCellValue();
                        if (!isTimeStepValue(valueTimeStep)) {
                            throw BusinessException.builder()
                                    .errorMessageArguments(List.of(columnNames.get(columnIndex), THERMAL_ME, trajectoryToUse))
                                    .message("Colunm {0} must be hourly or annual in {1} trajectory {2}")
                                    .httpStatus(HttpStatus.BAD_REQUEST)
                                    .build();
                        }
                        if (isTimeStepHourly(valueTimeStep)) {
                            String folderName = (String) entry.getValue().value;
                            boolean hasModulation = checkModulationFile(trajectoryPath, folderName, trajectoryToUse);
                            if (!hasModulation) {
                                throw BusinessException.builder()
                                        .errorMessageArguments(List.of(THERMAL_ME, toTitleCase(folderName), trajectoryToUse))
                                        .message("Missing {0} {1} TS in trajectory {2}")
                                        .httpStatus(HttpStatus.BAD_REQUEST)
                                        .build();
                            }
                        } 
                        break;

                    case CheckValueType.NUMERIC:
                        if (!isNumericCellAndNotBlank(cell)) {
                            throw BusinessException.builder()
                                    .errorMessageArguments(List.of(columnNames.get(columnIndex), THERMAL_ME, trajectoryToUse))
                                    .message("Column {0} must be numeric in {1} trajectory {2}")
                                    .httpStatus(HttpStatus.BAD_REQUEST)
                                    .build();
                        } 
                        break;
                }
            }

            ThermalMeEntity thermalMeEntity = ThermalMeEntity.builder()
                    .node(row.getCell(0).getStringCellValue())
                    .groupName(row.getCell(1).getStringCellValue())
                    .clusterName(row.getCell(2).getStringCellValue())
                    .enabled(getBooleanCell(row,3))
                    .nominalCapacity(row.getCell(4).getNumericCellValue())
                    .nbUnit((int) row.getCell(5).getNumericCellValue())
                    .marginalCost(row.getCell(6).getNumericCellValue())
                    .marginalCostTimestep(row.getCell(7).getStringCellValue())
                    .marginalCostModulation((int) row.getCell(8).getNumericCellValue())
                    .marketBidCost(row.getCell(9).getNumericCellValue())
                    .marketBidCostTimestep(row.getCell(10).getStringCellValue())
                    .marketBidCostModulation((int) row.getCell(11).getNumericCellValue())
                    .mustRun(getBooleanCell(row, 12))
                    .mrTimestep(row.getCell(13).getStringCellValue())
                    .mrModulation((int) row.getCell(14).getNumericCellValue())
                    .cmTimestep(row.getCell(15).getStringCellValue())
                    .cmModulation((int) row.getCell(16).getNumericCellValue())
                    .trajectory(trajectory)
                    .build();

            thermalMeEntities.add(thermalMeEntity);
        }
        trajectory.setThermalMeEntities(thermalMeEntities);
        return thermalMeEntities;
    }

    private boolean isTimeStepValue(String value) {
        return Optional.ofNullable(value)
                .map(String::toLowerCase)
                .map(TIMESTEP_VALUES::contains)
                .orElse(false);
    }


    private boolean isTimeStepHourly(String value) {
        return value != null && TIMESTEP_VALUES.getFirst().equalsIgnoreCase(value);
    }

    private boolean checkModulationFile(
            Path trajectoryPath,
            String folderName,
            String trajectoryToUse
    ) {
        String modulationTrajectoryName = folderName + "_" + trajectoryToUse + ".xlsx";

        try (Stream<Path> stream = Files.list(trajectoryPath)) {
            Optional<Path> folder = stream
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().equals(folderName))
                    .findFirst();

            if (folder.isEmpty()) {
                return false;
            }
            
            Path modulationFile = folder.get().resolve(modulationTrajectoryName);

            return Files.exists(modulationFile) && Files.isRegularFile(modulationFile);

        } catch (IOException e) {
            return false;
        }
    }

    private static String toTitleCase(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        return Arrays.stream(value.split("_"))
                .filter(s -> !s.isEmpty())
                .map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }
}
