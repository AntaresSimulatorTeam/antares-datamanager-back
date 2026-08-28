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
import com.rte_france.antares.datamanager_back.service.p2g.P2gFileProcessorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static com.rte_france.antares.datamanager_back.util.Utils.*;
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
    private final String capacity = "capacity";
    private final String costs = "costs";
    private static final String SHEET_PARAMETERS = "parameters";
    private static final String SHEET_COSTS = "costs";
    private static final Set<String> REQUIRED_PARAMETERS_NAMES = Set.of(
            "FC_electrolyseur",
            "Facteur_surdimension_ENR",
            "Part_PV_mix"
    );
    
    private static final Map<String, Integer> REQUIRED_COLUMN_NAMES = Map.of(
            "area_antares_name", 2,
            "P2G_fatalband",3,
            "P2G_asservi",4,
            "P2G_methanation",6,
            "P2G_base_eff",7,
            "To_Links_p2G_marg",9,
            "To_Links_p2G_base",10
    );
    
    private static final Map<String, Integer> REQUIRED_COSTS_COLUMN_NAMES = Map.of(
            "type P2G", 0,
            "modulation",2
    );
    
    private static final String[] REQUIRED_TYPE_NAMES = {
            "Marginal",
            "Base",
            "Asservi",
            "Methanation"
    };

    private static final String MODULATION_PREFIX = "MB_MC_modulation";
    
    private Map<String, Path> validateRequiredFiles(Path trajectoryFilePath) throws IOException {
        List<String> missingFiles = new ArrayList<>();
        Map<String, Path> paths = new HashMap<>();

        try (Stream<Path> stream = Files.list(trajectoryFilePath)) {
            List<Path> allFiles = stream.toList();

            for (var fileName : REQUIRED_FILES) {
                Path match = allFiles.stream()
                        .filter(p -> {
                            if (!p.getFileName().toString().equals(fileName)) {
                                missingFiles.add(fileName);
                                return false;
                            } else {
                                return true;
                            }
                        })
                        .findFirst()
                        .orElse(null);
 
                var type = match.getFileName().toString().contains("capacity") ? capacity : costs;
                paths.put(type, match);
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

    @Override
    public TrajectoryEntity processCapacityP2gFile(
            String trajectoryToUse,
            String horizon,
            Integer studyId,
            String areaParam,
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
        
       
        try (InputStream inputStream = Files.newInputStream(files.get(capacity));
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            List<String> missingTabs = new ArrayList<>();
            // Build parameters entities
            List<P2GParametersEntity> parametersEntities = buildParametersEntities(workbook, trajectory, missingTabs, horizon);
            
            List<P2GCapacityEntity> capacityEntities = buildCapacityEntities(workbook, trajectory, missingTabs, horizon, studyId);

            if (!missingTabs.isEmpty()) {
                throw BusinessException.builder()
                        .errorMessageArguments(List.of(String.join(", ", missingTabs), trajectoryToUse))
                        .message("Missing tab {0} in P2G trajectory {1}")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            if (parametersEntities.isEmpty() || capacityEntities.isEmpty()) {
                String dataMissing = capacityEntities.isEmpty() ? horizon : SHEET_PARAMETERS;
                throw BusinessException.builder()
                        .errorMessageArguments(List.of(dataMissing, trajectoryToUse))
                        .message("No data in {0} tab in AdequacyPatch trajectory {1}")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            trajectory.setP2gParametersEntities(parametersEntities);
            trajectory.setP2gCapacityEntities(capacityEntities);
            
        } catch (IOException e) {
            throw TechnicalException.builder()
                    .message("Could not process adequacy file: " + e.getMessage())
                    .cause(e)
                    .build();
        }

        try (InputStream inputStream = Files.newInputStream(files.get(costs));
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            // Build cost entities
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

        } catch (IOException e) {
            throw TechnicalException.builder()
                    .message("Could not process adequacy file: " + e.getMessage())
                    .cause(e)
                    .build();
        }

        // Sauvegarder uniquement si validation OK
        return trajectoryRepository.save(trajectory);
    }

    private List<P2GParametersEntity> buildParametersEntities(Workbook workbook, TrajectoryEntity trajectory, List<String> missingTabs, String horizon) {
        Sheet sheet = workbook.getSheet(SHEET_PARAMETERS);
        if (sheet == null) {
            missingTabs.add(SHEET_PARAMETERS);
            return Collections.emptyList();
        }
        
        Row header = sheet.getRow(0);
        int lastCol = header.getLastCellNum();
        int yearColIndex = -1;
        yearColIndex = getYearColIndex(1, lastCol, header, horizon, yearColIndex);
        if (yearColIndex == -1) {
            throw BusinessException.builder()
                    .message("Horizon '" + horizon + "' in parameters tab in P2G Capacity trajectory " + trajectory.getFileName())
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
      
        List<P2GParametersEntity> parameters = new ArrayList<>();
        List<String> missingParameters = new ArrayList<>();
        List<String> nonNumericParameters = new ArrayList<>();
        Map<String, Double> parametersMap = new HashMap<>();
        for (Row row : sheet) {
            if (row.getRowNum() == 0 || isRowEmpty(row)) continue;
            String parameterName = Objects.toString(getCellValue(row, 0), null);
            Cell parameterCell = row.getCell(yearColIndex);
            if (!REQUIRED_PARAMETERS_NAMES.contains(parameterName)) {
                missingParameters.add(parameterName);
                continue;
            }
            if (!isNumericCell(parameterCell)) {
                nonNumericParameters.add(parameterName);
                continue;
            }
            parametersMap.put(parameterName, parameterCell.getNumericCellValue());
       }

        if (!missingParameters.isEmpty()) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(String.join(", ", missingParameters), trajectory.getFileName()))
                    .message("Missing parameters {0} in parameters tab in P2G Capacity trajectory {1}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        } else if (!nonNumericParameters.isEmpty()) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(String.join(", ", missingParameters), trajectory.getFileName()))
                    .message("Parameter Value {0} must be numeric in parameters tab in P2G Capacity trajectory {1}")
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

        checkMissingColumns(sheet, REQUIRED_COLUMN_NAMES.keySet().toArray(new String[0]), trajectory.getFileName(), TrajectoryType.P2G_CAPACITY_COST);
        List<String> studyAreas = loadStudyAreas(studyId);

        List<P2GCapacityEntity> capacities = new ArrayList<>();
        List<String> areaNames = new ArrayList<>();
        List<String> columnsNonNumeric = new ArrayList<>();
        for (Row row : sheet) {
            if (row.getRowNum() == 0 || isRowEmpty(row)) continue;
            String areaName = Objects.toString(getCellValue(row, 0), null);
            if (studyAreas.contains(areaName)) {
                areaNames.add(areaName);
                continue;
            }
            for (Map.Entry<String, Integer> column : REQUIRED_COLUMN_NAMES.entrySet()) {
                Cell cell = row.getCell(column.getValue());

                if (!isNumericCell(cell)) {
                    columnsNonNumeric.add(column.getKey());
                }
            }
        }

        if (areaNames.isEmpty()) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(trajectory.getFileName()))
                    .message("No area of the study is present in horizon tab in P2G Capacity trajectory {1}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        if (!columnsNonNumeric.isEmpty()) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(String.join(", ", columnsNonNumeric), trajectory.getFileName()))
                    .message("Column value {0} must be numeric in horizon tab in P2G Capacity trajectory {1}")
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
                    .message("Missing horizon '" + horizon + "' in P2G Costs trajectory " + trajectory.getFileName())
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        List<P2GCostEntity> costs = new ArrayList<>();
        for (Row row : sheet) {
            if (row.getRowNum() == 0 || isRowEmpty(row)) continue;

            String typeName = Objects.toString(getCellValue(row, 0), null);
            String modulationValue = Objects.toString(getCellValue(row, 2), null);
            Cell costCell = row.getCell(yearColIndex);
            
            if(!Arrays.asList(REQUIRED_TYPE_NAMES).contains(typeName)) {
                throw BusinessException.builder()
                        .errorMessageArguments(List.of(typeName, trajectory.getFileName()))
                        .message("Missing P2G Type {0} in P2G Costs trajectory {1}")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
            if (!isNumericCell(costCell)) {
                throw BusinessException.builder()
                        .errorMessageArguments(List.of(typeName, trajectory.getFileName()))
                        .message("P2G Type Value {0} must be numeric in P2G Costs trajectory {1}")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            P2GCostEntity costEntity = P2GCostEntity.builder()
                    .type(typeName)
                    .modulation(modulationValue)
                    .cost(costCell.getNumericCellValue())
                    .trajectory(trajectory)
                    .build();

            costs.add(costEntity);
        }
        

        return costs;
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
            String areaParam,
            boolean isCivilYear
    ) throws IOException {
        Path directoryPath = trajectoryService.normalizeAndValidateDirectory(TrajectoryType.P2G_MARKET_MODULATION, null, null);
        Path trajectoryFilePath = validateAndResolveTrajectoryPath(directoryPath, trajectoryToUse);
        String horizonYear = horizon.split("-")[1];
        String modulationFileName = MODULATION_PREFIX+"_"+trajectoryToUse+"_"+horizonYear+".csv";
        
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
    
}

