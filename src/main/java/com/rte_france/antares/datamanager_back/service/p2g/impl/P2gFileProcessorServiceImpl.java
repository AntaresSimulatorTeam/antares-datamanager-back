package com.rte_france.antares.datamanager_back.service.p2g.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.p2g.P2GCapacityEntity;
import com.rte_france.antares.datamanager_back.repository.model.p2g.P2GCostEntity;
import com.rte_france.antares.datamanager_back.repository.model.p2g.P2GParametersEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.p2g.P2gFilePrefixes;
import com.rte_france.antares.datamanager_back.service.p2g.P2gFileProcessorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.rte_france.antares.datamanager_back.util.CastCellUtil.castDouble;
import static com.rte_france.antares.datamanager_back.util.Utils.*;
import com.rte_france.antares.datamanager_back.util.Utils;
import com.rte_france.antares.datamanager_back.util.Utils.FormulaAndValue;
import static com.rte_france.antares.datamanager_back.util.Utils.checkMissingColumns;
import static com.rte_france.antares.datamanager_back.util.Utils.getCellValue;
import static com.rte_france.antares.datamanager_back.util.excel_file_validators.ExcelCommonValidator.isRowEmpty;

@Slf4j
@Service
@RequiredArgsConstructor
public class P2gFileProcessorServiceImpl implements P2gFileProcessorService {
    private final TrajectoryRepository trajectoryRepository;
    private final AreaRepository areaRepository;
    private final TrajectoryServiceImpl trajectoryService;
    private static final String[] REQUIRED_FILES = {
            "P2G_capacity.xlsx",
            "P2G_costs.xlsx"
    };
    private static final String CAPACITY = "capacity";
    private static final String SHEET_PARAMETERS = "parameters";
    private static final String SHEET_COSTS = "costs";
    private static final Set<String> REQUIRED_PARAMETERS_NAMES = Set.of(
            "FC_electrolyseur",
            "Facteur_surdimension_ENR",
            "Part_PV_mix"
    );
    
    private static final Map<String, Integer> NUMERIC_COLUMN_NAMES = new LinkedHashMap<>(Map.of(
            "P2G_fatalband",3,
            "P2G_asservi",4,
            "P2G_methanation",6,
            "P2G_base_eff",7,
            "To_Links_p2G_marg",9,
            "To_Links_p2G_base (P2G base + fatal)",10
    ));

    private static final Map<String, Integer> STRING_COLUMN_NAMES = Map.of(
            "area_antares_name",2
    );
    
    private static final Map<String, Integer> REQUIRED_COSTS_COLUMN_NAMES = Map.of(
            "type P2G", 0,
            "modulation",2
    );
    
    private static final Set<String> REQUIRED_TYPE_NAMES = Set.of(
            "Marginal",
            "Base",
            "Asservi",
            "Methanation"
    );

    @Override
    public TrajectoryEntity processCapacityP2gFile(
            String trajectoryToUse,
            String horizon,
            Integer studyId,
            boolean isCivilYear
    ) throws IOException {
        Path directoryPath = trajectoryService.normalizeAndValidateDirectory(TrajectoryType.P2G_CAPACITY_COST, null, null);
        Path trajectoryFilePath = validateAndResolveTrajectoryPath(directoryPath, trajectoryToUse);

        Map<String, Path> files = validateRequiredFiles(trajectoryFilePath);

        // Construire la trajectoire complète AVANT la validation
        TrajectoryEntity trajectory = trajectoryService.buildDirectoryTrajectory(
                TrajectoryType.P2G_CAPACITY_COST.name(),
                trajectoryToUse,
                trajectoryFilePath,
                horizon,
                null,
                null
        );
        
        try (InputStream capacityInputStream = Files.newInputStream(files.get(CAPACITY));
             Workbook capacityWorkbook = WorkbookFactory.create(capacityInputStream);
             InputStream costsInputStream = Files.newInputStream(files.get(SHEET_COSTS));
             Workbook costsWorkbook = WorkbookFactory.create(costsInputStream)) {

            processCapacityAndParameters(capacityWorkbook, trajectory, trajectoryToUse, horizon, studyId);
            processCosts(costsWorkbook, trajectory, trajectoryToUse, horizon);

        } catch (IOException e) {
            throw TechnicalException.builder()
                    .message("Could not process P2G file: " + e.getMessage())
                    .cause(e)
                    .build();
        }

        // Sauvegarder uniquement si validation OK
        return trajectoryRepository.save(trajectory);
    }

    private void processCapacityAndParameters(Workbook workbook, TrajectoryEntity trajectory, String trajectoryToUse, String horizon, Integer studyId) {
        List<String> missingParametersTabs = new ArrayList<>();
        List<P2GParametersEntity> parametersEntities = buildParametersEntities(workbook, trajectory, missingParametersTabs, horizon);

        if (!missingParametersTabs.isEmpty()) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(trajectoryToUse))
                    .message("Parameters tab does not exist in the P2G Capacity trajectory {0}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        
        List<String> missingHorizonTabs = new ArrayList<>();
        List<P2GCapacityEntity> capacityEntities = buildCapacityEntities(workbook, trajectory, missingHorizonTabs, horizon, studyId);

        if (!missingHorizonTabs.isEmpty()) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(String.join(", ", missingHorizonTabs), trajectoryToUse))
                    .message("Horizon tab {0} does not exist in the P2G Capacity trajectory {1}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        if (parametersEntities.isEmpty() || capacityEntities.isEmpty()) {
            String dataMissing = capacityEntities.isEmpty() ? horizon : SHEET_PARAMETERS;
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(dataMissing, trajectoryToUse))
                    .message("No data in {0} tab in P2G trajectory {1}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        trajectory.setP2gParametersEntities(parametersEntities);
        trajectory.setP2gCapacityEntities(capacityEntities);
    }

    private Map<String, Path> validateRequiredFiles(Path trajectoryFilePath) {
        List<String> missingFiles = new ArrayList<>();
        Map<String, Path> paths = new HashMap<>();

        for (String fileName : REQUIRED_FILES) {
            Path filePath = trajectoryFilePath.resolve(fileName);
            if (!Files.exists(filePath)) {
                missingFiles.add(fileName);
            } else {
                String type = fileName.contains(CAPACITY) ? CAPACITY : SHEET_COSTS;
                paths.put(type, filePath);
            }
        }

        if (!missingFiles.isEmpty()) {
            throw BusinessException.builder()
                    .message("Required files are missing: " + String.join(", ", missingFiles))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        return paths;
    }

    private void processCosts(Workbook workbook, TrajectoryEntity trajectory, String trajectoryToUse, String horizon) {
        List<String> missingCostsTabs = new ArrayList<>();
        List<P2GCostEntity> costEntities = buildCostsEntities(workbook, trajectory, missingCostsTabs, horizon);

        if (!missingCostsTabs.isEmpty()) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(SHEET_COSTS, trajectoryToUse))
                    .message("Missing tab {0} in P2G trajectory {1}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        trajectory.setP2gCostEntities(costEntities);
    }

    private List<P2GParametersEntity> buildParametersEntities(Workbook workbook, TrajectoryEntity trajectory, List<String> missingTabs, String horizon) {
        Sheet sheet = workbook.getSheet(SHEET_PARAMETERS);
        if (sheet == null) {
            missingTabs.add(SHEET_PARAMETERS);
            return Collections.emptyList();
        }
        
        Row header = sheet.getRow(0);
        if (header == null) {
            missingTabs.add(SHEET_PARAMETERS);
            return Collections.emptyList();
        }
        int lastCol = header.getLastCellNum();
        int yearColIndex = -1;
        yearColIndex = getYearColIndex(1, lastCol, header, horizon, yearColIndex);
        if (yearColIndex == -1) {
            throw BusinessException.builder()
                    .message("Missing horizon '" + horizon + "' in parameters tab in P2G Capacity trajectory " + trajectory.getFileName())
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
      
        List<P2GParametersEntity> parameters = new ArrayList<>();
        List<String> requiredParameters = new ArrayList<>();
        Map<String, Double> parametersMap = new HashMap<>();
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        for (Row row : sheet) {
            if (row.getRowNum() == 0 || isRowEmpty(row)) continue;
            String parameterName = Objects.toString(getCellValue(row, 0, evaluator), null);
            Cell parameterCell = row.getCell(yearColIndex);
            if (REQUIRED_PARAMETERS_NAMES.contains(parameterName)) {
                requiredParameters.add(parameterName);
            }
            if (!isNumericCell(parameterCell)) {
                throw BusinessException.builder()
                        .errorMessageArguments(List.of(parameterName != null ? parameterName : "", trajectory.getFileName()))
                        .message("Parameter Value {0} must be numeric in parameters tab in P2G Capacity trajectory {1}")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
            parametersMap.put(parameterName, parameterCell.getNumericCellValue());
       }

        if (requiredParameters.size() != REQUIRED_PARAMETERS_NAMES.size()) {
            var missingParameters = REQUIRED_PARAMETERS_NAMES.stream()
                    .filter(param -> !requiredParameters.contains(param))
                    .toList();
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(String.join(", ", missingParameters), trajectory.getFileName()))
                    .message("Missing parameters {0} in parameters tab in P2G Capacity trajectory {1}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        } 

        P2GParametersEntity parametersEntity = P2GParametersEntity.builder()
                .fcElectrolyseur(parametersMap.get("FC_electrolyseur"))
                .facteurSurdimensionEnr(parametersMap.get("Facteur_surdimension_ENR"))
                .partPvMix(parametersMap.get("Part_PV_mix"))
                .trajectory(trajectory)
                .build();

        parameters.add(parametersEntity);
                
        return parameters;
    }

    private List<P2GCapacityEntity> buildCapacityEntities(Workbook workbook, TrajectoryEntity trajectory, List<String> missingTabs, String horizon, Integer studyId) {
        Sheet sheet = workbook.getSheet(horizon);
        if (sheet == null) {
            missingTabs.add(horizon);
            return Collections.emptyList();
        }
        Map<String, Integer> allColumns = new LinkedHashMap<>();
        allColumns.putAll(STRING_COLUMN_NAMES);
        allColumns.putAll(NUMERIC_COLUMN_NAMES);
        checkMissingColumns(sheet, allColumns.keySet().toArray(new String[0]), trajectory.getFileName(), TrajectoryType.P2G_CAPACITY_COST);
        List<String> studyAreas = loadStudyAreas(studyId);

        List<P2GCapacityEntity> capacities = new ArrayList<>();
        List<String> areaNames = new ArrayList<>();
        List<String> columnsNonNumeric = new ArrayList<>();
        Map<String, Double> capacityMap = new HashMap<>();
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        for (Row row : sheet) {
            if (isRowEmpty(row) || row.getRowNum() == 0) {
                continue;
            }

            String areaName = Objects.toString(getCellValue(row, 2, evaluator), null);
            if (studyAreas.contains(areaName)) {
                areaNames.add(areaName);

                for (Map.Entry<String, Integer> column : NUMERIC_COLUMN_NAMES.entrySet()) {
                    Cell capacityCell = row.getCell(column.getValue());
                    if (!isNumericCellWithFormula(capacityCell, evaluator)) {
                        columnsNonNumeric.add(column.getKey());
                        throw BusinessException.builder()
                                .errorMessageArguments(List.of(String.join(", ", columnsNonNumeric), trajectory.getFileName()))
                                .message("Column value {0} must be numeric in horizon tab in P2G Capacity trajectory {1}")
                                .httpStatus(HttpStatus.BAD_REQUEST)
                                .build();
                    } else {
                        FormulaAndValue formulaAndValue = getFormulaAndValue(capacityCell, evaluator);
                        Double numVal = formulaAndValue.getNumericValue();
                        capacityMap.put(column.getKey(), numVal);
                    }
                }

                P2GCapacityEntity capacityEntity = P2GCapacityEntity.builder()
                        .area(areaName)
                        .baseFatalBand(capacityMap.get("P2G_fatalband"))
                        .baseEff(capacityMap.get("P2G_base_eff"))
                        .baseCapacity(capacityMap.get("To_Links_p2G_base (P2G base + fatal)"))
                        .margCapacity(capacityMap.get("To_Links_p2G_marg"))
                        .methanationCapacity(capacityMap.get("P2G_methanation"))
                        .asserviCapacity(capacityMap.get("P2G_asservi"))
                        .trajectory(trajectory)
                        .build();

                capacities.add(capacityEntity);
            }
        }

        if (areaNames.isEmpty()) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(trajectory.getFileName()))
                    .message("No area of the study is present in horizon tab in P2G Capacity trajectory {0}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        
        return capacities;
    }

    private List<P2GCostEntity> buildCostsEntities(Workbook workbook, TrajectoryEntity trajectory, List<String> missingTabs, String horizon) {
        Sheet sheet = workbook.getSheet(SHEET_COSTS);
        if (sheet == null) {
            missingTabs.add(horizon);
            return Collections.emptyList();
        }

        checkMissingColumns(sheet, REQUIRED_COSTS_COLUMN_NAMES.keySet().toArray(new String[0]), trajectory.getFileName(), TrajectoryType.P2G_CAPACITY_COST);

        Row header = sheet.getRow(0);
        int lastCol = header.getLastCellNum();
        int yearColIndex = -1;
        String horizonYear = horizon.split("-")[1];
        yearColIndex = getYearColIndex(1, lastCol, header, horizonYear, yearColIndex);
        if (yearColIndex == -1) {
            throw BusinessException.builder()
                    .message("Missing horizon '" + horizonYear + "' in P2G Costs trajectory " + trajectory.getFileName())
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        List<P2GCostEntity> costsEntities = new ArrayList<>();
        List<String> requiredTypes = new ArrayList<>();
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        for (int r = 0; r <= 4; r++) {
            Row row = sheet.getRow(r);
            if (row == null || isRowEmpty(row)|| row.getRowNum() == 0) continue;

            String typeName = Objects.toString(getCellValue(row, 0, evaluator), null);
            String modulationValue = Objects.toString(getCellValue(row, 2, evaluator), null);
            Cell costCell = row.getCell(yearColIndex);

            if (typeName != null && REQUIRED_TYPE_NAMES.contains(typeName)) {
                requiredTypes.add(typeName);
            }
            
            if (!isNumericCell(costCell)) {
                throw BusinessException.builder()
                        .errorMessageArguments(List.of(typeName != null ? typeName : "", trajectory.getFileName()))
                        .message("P2G Type Value {0} must be numeric in P2G Costs trajectory {1}")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
            
            P2GCostEntity costEntity = P2GCostEntity.builder()
                    .type(typeName)
                    .modulation(modulationValue)
                    .cost(castDouble(costCell.getNumericCellValue(), typeName, r))
                    .trajectory(trajectory)
                    .build();

            costsEntities.add(costEntity);
        }

        if (requiredTypes.size() != REQUIRED_TYPE_NAMES.size()) {
            var missingType = REQUIRED_TYPE_NAMES.stream()
                    .filter(param -> !requiredTypes.contains(param))
                    .toList();
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(String.join(", ", missingType), trajectory.getFileName()))
                    .message("Missing P2G Type {0} in P2G Costs trajectory {1}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        

        return costsEntities;
    }
    
    public List<String> loadStudyAreas(Integer studyId) {
        return areaRepository.findAllByStudyId(studyId)
                .stream()
                .map(a -> a.getName().toUpperCase())
                .toList();
    }
    
    @Override
    public TrajectoryEntity processModulationP2gFile(
            String trajectoryToUse,
            String horizon,
            Integer studyId,
            boolean isCivilYear
    ) throws IOException {
        Path directoryPath = trajectoryService.normalizeAndValidateDirectory(TrajectoryType.P2G_MARKET_MODULATION, null, null);
        Path trajectoryFilePath = validateAndResolveTrajectoryPath(directoryPath, trajectoryToUse);
        String horizonYear = horizon.split("-")[1];
        String modulationFileName = P2gFilePrefixes.MODULATION_PREFIX+"_"+trajectoryToUse+"_"+horizonYear+".csv";
        
        Path modulationFilePath = trajectoryFilePath.resolve(modulationFileName);
        
        if (Files.notExists(modulationFilePath)) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(modulationFileName, trajectoryToUse))
                    .message("Missing {0} in P2G Market Bid trajectory {1}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        // Construire la trajectoire complète AVANT la validation
        TrajectoryEntity trajectory = trajectoryService.buildDirectoryTrajectory(
                TrajectoryType.P2G_MARKET_MODULATION.name(),
                trajectoryToUse,
                trajectoryFilePath,
                horizon,
                null,
                null
        );
        
        // Sauvegarder uniquement si validation OK
        return trajectoryRepository.save(trajectory);
    }

    public FormulaAndValue getFormulaAndValue(Cell cell) {
        return Utils.getFormulaAndValue(cell);
    }

    public FormulaAndValue getFormulaAndValue(Cell cell, FormulaEvaluator evaluator) {
        return Utils.getFormulaAndValue(cell, evaluator);
    }
    
}

