package com.rte_france.antares.datamanager_back.service.thermal_me.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.ThermalMeEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.multi_energy.MultiEnergyCoherenceCheckService;
import com.rte_france.antares.datamanager_back.service.thermal_me.ThermalMeFileProcessorService;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum.ThermalCapacityMeColumns;
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
    private final MultiEnergyCoherenceCheckService multiEnergyCoherenceCheckService;
    
    private static final String THERMAL_ME = "THERMAL_ME";
    private static final String FILE_EXTENSION_XLSX = ".xlsx";
    private static final String MARGINAL_COST_MODULATION = "marginal_cost_modulation";
    private static final String MARKET_BID_MODULATION = "market_bid_modulation";
    private static final String MUST_RUN_MODULATION = "must_run_modulation";
    private static final String CAPACITY_MODULATION = "capacity_modulation";
    
    private static final List<String> TIMESTEP_VALUES = List.of("hourly", "annual");
    private static final Map<Integer, String> MODULATION_FOLDERS = Map.of(ThermalCapacityMeColumns.MARGINAL_COST_TIMESTEP.getIndex(), MARGINAL_COST_MODULATION, ThermalCapacityMeColumns.MARKET_BID_COST_TIMESTEP.getIndex(), MARKET_BID_MODULATION, ThermalCapacityMeColumns.MR_TIMESTEP.getIndex(), MUST_RUN_MODULATION, ThermalCapacityMeColumns.CM_TIMESTEP.getIndex(), CAPACITY_MODULATION);
    
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
        Set<Integer> lengthValues = Set.of(ThermalCapacityMeColumns.NODE.getIndex(), ThermalCapacityMeColumns.GROUP_NAME.getIndex(), ThermalCapacityMeColumns.CLUSTER_NAME.getIndex());
        Set<Integer> booleanValues = Set.of(ThermalCapacityMeColumns.ENABLED.getIndex(), ThermalCapacityMeColumns.MUST_RUN.getIndex());
        Set<Integer> timestepValues = Set.of(ThermalCapacityMeColumns.MARGINAL_COST_TIMESTEP.getIndex(), ThermalCapacityMeColumns.MARKET_BID_COST_TIMESTEP.getIndex(), ThermalCapacityMeColumns.MR_TIMESTEP.getIndex(), ThermalCapacityMeColumns.CM_TIMESTEP.getIndex());

        for (int i = 0; i <= ThermalCapacityMeColumns.CM_MODULATION.getIndex(); i++) {
            CHECK_VALUE_LIST.put(
                    i,
                    buildCheckValueDefinition(i, lengthValues, booleanValues, timestepValues)
            );
        }
    }

    private static CheckValueDefinition<?> buildCheckValueDefinition(
            int index,
            Set<Integer> lengthValues,
            Set<Integer> booleanValues,
            Set<Integer> timestepValues) {

        if (booleanValues.contains(index)) {
            return new CheckValueDefinition<>(CheckValueType.BOOLEAN, null);
        }

        if (timestepValues.contains(index)) {
            return new CheckValueDefinition<>(CheckValueType.TIMESTEP, MODULATION_FOLDERS.get(index));
        }

        if (lengthValues.contains(index)) {
            return new CheckValueDefinition<>(CheckValueType.LENGTH, getLengthValue(index));
        }

        return new CheckValueDefinition<>(CheckValueType.NUMERIC, null);
    }

    private static int getLengthValue(int index) {
        if(index == ThermalCapacityMeColumns.NODE.getIndex()){
            return 60;
        } else if(index == ThermalCapacityMeColumns.GROUP_NAME.getIndex()){
            return 20;
        }
        return 40;
    }

    @Transactional(rollbackFor = {IOException.class})
    @Override
    public TrajectoryEntity processThermalMeFile(String trajectoryToUse, String horizon, Integer studyId) throws IOException {
        return saveThermalMeTrajectoryInDb(trajectoryToUse, horizon, studyId);
    }

    public TrajectoryEntity saveThermalMeTrajectoryInDb(String trajectoryToUse, String horizon, Integer studyId) throws IOException {
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
            }

            TrajectoryEntity newTrajectory = buildNewTrajectory(TrajectoryType.THERMAL_CAPACITY_ME, trajectoryToUse, horizon, trajectoryPath, userNni);
            existingTrajectoryOpt.ifPresent(existing -> newTrajectory.setVersion(existing.getVersion() + 1));

            Path baseDirectory = Path.of(nasDir, trajFilePath, directoryByType);
            Set<String> areasMeList = multiEnergyCoherenceCheckService.getAreasMe(studyId);
            buildEntities(sheet, newTrajectory, trajectoryToUse, baseDirectory, areasMeList);
            return trajectoryRepository.save(newTrajectory);

        } catch (IOException e) {
            throw TechnicalException.builder()
                    .message("Could not process THERMAL ME file: " + e.getMessage())
                    .cause(e)
                    .build();
        }
    }

    private void buildEntities(Sheet sheet, TrajectoryEntity trajectory, String trajectoryToUse, Path trajectoryPath, Set<String> areasMeList) {
        List<ThermalMeEntity> thermalMeEntities = new ArrayList<>();
        List<String> columnNames = new ArrayList<>();
        Set<String> seenCombinations = new HashSet<>();
        
        for (Row row : sheet) {
            if (row == null || isRowEmpty(row) || row.getRowNum() < 3) continue;
            if (row.getRowNum() == 3) {
                int lastCol = getRealLastColumn(row);
                for (int r = 0; r <= lastCol; r++) {
                    columnNames.add(Objects.toString(getCellValue(row, r)));   
                }
                continue;
            }
            
            String node = Objects.toString(getCellValue(row, ThermalCapacityMeColumns.NODE.getIndex()), "").trim();
            if (!areasMeList.contains(node.toUpperCase(Locale.ROOT))) continue;
            
            checkRow(row, columnNames, trajectoryToUse, trajectoryPath);

            // --- Récupération du cluster_name ---
            String clusterName = Objects.toString(getCellValue(row, ThermalCapacityMeColumns.CLUSTER_NAME.getIndex()), "").trim();

            // --- Validation d'unicité (insensible à la casse) ---
            String uniqueKey = node.toUpperCase() + "|" + clusterName.toUpperCase();
            if (!seenCombinations.add(uniqueKey)) {
                throw BusinessException.builder()
                        .message("Duplicate entry detected in {0} trajectory {1}: node {2} and cluster {3} is defined more than once for this horizon.")
                        .errorMessageArguments(List.of(THERMAL_ME, trajectoryToUse, node, clusterName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            ThermalMeEntity thermalMeEntity = ThermalMeEntity.builder()
                    .node(node)
                    .groupName(row.getCell(ThermalCapacityMeColumns.GROUP_NAME.getIndex()).getStringCellValue())
                    .clusterName(clusterName)
                    .enabled(getBooleanCell(row,ThermalCapacityMeColumns.ENABLED.getIndex()))
                    .nominalCapacity(row.getCell(ThermalCapacityMeColumns.NOMINAL_CAPACITY.getIndex()).getNumericCellValue())
                    .nbUnit((int) row.getCell(ThermalCapacityMeColumns.NB_UNIT.getIndex()).getNumericCellValue())
                    .marginalCost(row.getCell(ThermalCapacityMeColumns.MARGINAL_COST.getIndex()).getNumericCellValue())
                    .marginalCostTimestep(row.getCell(ThermalCapacityMeColumns.MARGINAL_COST_TIMESTEP.getIndex()).getStringCellValue())
                    .marginalCostModulation(row.getCell(ThermalCapacityMeColumns.MARGINAL_COST_MODULATION.getIndex()).getNumericCellValue())
                    .marketBidCost(row.getCell(ThermalCapacityMeColumns.MARKET_BID_COST.getIndex()).getNumericCellValue())
                    .marketBidCostTimestep(row.getCell(ThermalCapacityMeColumns.MARKET_BID_COST_TIMESTEP.getIndex()).getStringCellValue())
                    .marketBidCostModulation(row.getCell(ThermalCapacityMeColumns.MARKET_BID_COST_MODULATION.getIndex()).getNumericCellValue())
                    .mustRun(getBooleanCell(row, ThermalCapacityMeColumns.MUST_RUN.getIndex()))
                    .mrTimestep(row.getCell(ThermalCapacityMeColumns.MR_TIMESTEP.getIndex()).getStringCellValue())
                    .mrModulation(row.getCell(ThermalCapacityMeColumns.MR_MODULATION.getIndex()).getNumericCellValue())
                    .cmTimestep(row.getCell(ThermalCapacityMeColumns.CM_TIMESTEP.getIndex()).getStringCellValue())
                    .cmModulation(row.getCell(ThermalCapacityMeColumns.CM_MODULATION.getIndex()).getNumericCellValue())
                    .trajectory(trajectory)
                    .build();

            thermalMeEntities.add(thermalMeEntity);
        }
        
        if (thermalMeEntities.isEmpty()) {
            throw BusinessException.builder()
                    .message("No area from the THERMAL_ME trajectory is present in the AREA_ME trajectory")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        trajectory.setThermalMeEntities(thermalMeEntities);
    }
    
    private void checkRow(Row row, List<String> columnNames, String trajectoryToUse, Path trajectoryPath) {

        if (getCellValue(row, ThermalCapacityMeColumns.NODE.getIndex()) == null) {
            throw BusinessException.builder()
                    .message("{0} column must be filled in {1} trajectory {2}")
                    .errorMessageArguments(List.of(columnNames.getFirst(), THERMAL_ME, trajectoryToUse))
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
                    checkValueLength(value, nbCharactersMax, columnNames.get(columnIndex), trajectoryToUse);
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
                    String folderName = (String) entry.getValue().value;
                    checkValueTimeStep(valueTimeStep, folderName, columnNames.get(columnIndex), trajectoryToUse, trajectoryPath);
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
    }
    
    private void checkValueTimeStep(String valueTimeStep, String folderName, String columnName, String trajectoryToUse, Path trajectoryPath) {
        if (!isTimeStepValue(valueTimeStep)) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(columnName, THERMAL_ME, trajectoryToUse))
                    .message("Column {0} must be hourly or annual in {1} trajectory {2}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        if (isTimeStepHourly(valueTimeStep)) {
            boolean hasModulation = checkModulationFile(trajectoryPath, folderName, trajectoryToUse);
            if (!hasModulation) {
                throw BusinessException.builder()
                        .errorMessageArguments(List.of(THERMAL_ME, toTitleCase(folderName), trajectoryToUse))
                        .message("Missing {0} {1} TS in trajectory {2}")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
        }
    }
    
    private void checkValueLength (String valueRow, int nbCharactersMax, String columnName, String trajectoryToUse) {
        if (valueRow != null && valueRow.length() > nbCharactersMax) {
            throw BusinessException.builder()
                    .message("{0} cannot exceed {1} characters in {2} trajectory {3}")
                    .errorMessageArguments(List.of(columnName, Integer.toString(nbCharactersMax), THERMAL_ME, trajectoryToUse))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
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
        String modulationTrajectoryName = folderName + "_" + trajectoryToUse + FILE_EXTENSION_XLSX;

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
