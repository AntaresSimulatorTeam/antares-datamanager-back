package com.rte_france.antares.datamanager_back.service.adequacy.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.settings.AdequacyModeEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.settings.AdequacySettingsEntity;
import com.rte_france.antares.datamanager_back.service.adequacy.AdequacyFileProcessorService;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
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
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.rte_france.antares.datamanager_back.util.Utils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdequacyFileProcessorServiceImpl implements AdequacyFileProcessorService {

    private final TrajectoryRepository trajectoryRepository;
    private final TrajectoryServiceImpl trajectoryService;
    private final AdequacyModeRepository adequacyModeRepository;
    private final AdequacySettingsRepository adequacySettingsRepository;
    private final StudyRepository studyRepository;
    private final AreaRepository areaRepository;

    private static final Map<String, BiConsumer<AdequacySettingsEntity, Object>> settingsSetters = new HashMap<>();
    private static final String FILE_NAME_DEFAULT = "default.xlsx";
    private static final String SHEET_SETTINGS = "settings";
    private static final String SHEET_PERIMETER = "perimetre";

    static {
        settingsSetters.put("include_adq_patch", (e, v) -> e.setIncludeAdqPatch((Boolean) v));
        settingsSetters.put("price_taking_order", (e, v) -> e.setPriceTakingOrder(v.toString()));
        settingsSetters.put("include_hurdle_cost_csr", (e, v) -> e.setIncludeHurdleCostCsr((Boolean) v));
        settingsSetters.put("check_csr_cost_function", (e, v) -> e.setCheckCsrCostFunction((Boolean) v));
        settingsSetters.put("threshold_initiate_curtailment_sharing_rule", (e, v) -> e.setThresholdInitiateCurtailmentSharingRule(castToInteger(v)));
        settingsSetters.put("threshold_display_local_matching_rule_violations", (e, v) -> e.setThresholdDisplayLocalMatchingRuleViolations(castToInteger(v)));
        settingsSetters.put("threshold_csr_variable_bounds_relaxation", (e, v) -> e.setThresholdCsrVariableBoundsRelaxation(castToInteger(v)));
        settingsSetters.put("redispatch", (e, v) -> e.setRedispatch((Boolean) v));
        settingsSetters.put("set_to_null_ntc_from_physical_out_to_physical_in_for_first_step",
                (e, v) -> e.setSetToNullNtcFromPhysicalOutToPhysicalInForFirstStep((Boolean) v));
    }

    private static Integer castToInteger(Object v) {
        if (v instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = IOException.class)
    public TrajectoryEntity processAdequacyFile(String trajectoryToUse, String horizon, Integer studyId, boolean isCivilYear) throws IOException {
        Path directoryPath = trajectoryService.normalizeAndValidateDirectory(TrajectoryType.ADEQUACY_PATCH, null, null);
        Path trajectoryFilePath = validateAndResolveTrajectoryPath(directoryPath, trajectoryToUse);

        Path defaultFile = findDefaultFile(trajectoryFilePath, trajectoryToUse);

        TrajectoryEntity trajectory = trajectoryService.buildDirectoryTrajectory(
                TrajectoryType.ADEQUACY_PATCH.name(),
                trajectoryToUse,
                trajectoryFilePath,
                horizon,
                null,
                null
        );

        try (InputStream inputStream = Files.newInputStream(defaultFile);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            
            List<String> missingTabs = new ArrayList<>();
            List<String> studyAreas = areaRepository.findAllByStudyId(studyId).stream().map(a -> a.getName().toUpperCase()).toList();
            List<AdequacyModeEntity> modes = buildAdequacyModeList(workbook, trajectory, missingTabs);

            Set<String> normalizedFileAreas = modes.stream()
                    .map(AdequacyModeEntity::getArea)
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());

            List<String> missingAreas = studyAreas.stream()
                    .filter(area -> !normalizedFileAreas.contains(area.toLowerCase()))
                    .toList();
                    
            List<AdequacySettingsEntity> settings = buildAdequacySettingsList(workbook, trajectory, missingTabs);
            
            if (!missingTabs.isEmpty()) {
                throw BusinessException.builder()
                        .errorMessageArguments(List.of(String.join(", ", missingTabs), trajectoryToUse))
                        .message("Missing tab {0} in AdequacyPatch trajectory {1}")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
            
            if (modes.isEmpty() || settings.isEmpty()) {
                String dataMissing = settings.isEmpty() ? SHEET_SETTINGS : SHEET_PERIMETER;
                throw BusinessException.builder()
                        .errorMessageArguments(List.of(dataMissing, trajectoryToUse))
                        .message("No data in {0} tab in AdequacyPatch trajectory {1}")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            if (!missingAreas.isEmpty()) {
                throw BusinessException.builder()
                        .errorMessageArguments(List.of(String.join(", ", missingAreas), trajectoryToUse))
                        .message("Missing area(s) {0} in AdequacyPatch trajectory {1}")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            trajectory.setAdequacyModeEntities(modes);
            trajectory.setAdequacySettingsEntities(settings);

            StudyEntity study = studyRepository.findById(studyId)
                    .orElseThrow(() -> TechnicalException.builder().message("Study not found with id " + studyId).build());

            trajectory.getScenarioEntities().add(study);
            study.getTrajectories().add(trajectory);

            adequacyModeRepository.saveAll(modes);
            adequacySettingsRepository.saveAll(settings);

            return trajectoryRepository.save(trajectory);

        } catch (IOException e) {
            throw TechnicalException.builder()
                    .message("Could not process adequacy file: " + e.getMessage())
                    .cause(e)
                    .build();
        }
    }

    private List<AdequacyModeEntity> buildAdequacyModeList(Workbook workbook, TrajectoryEntity trajectory, List<String> missingTabs) {
        Sheet sheet = workbook.getSheet(SHEET_PERIMETER);
        if (sheet == null) {
            missingTabs.add(SHEET_PERIMETER);
            return Collections.emptyList();
        }
        List<AdequacyModeEntity> modes = new ArrayList<>();
        for (Row row : sheet) {
            if (row.getRowNum() == 0 || isRowEmpty(row)) continue;
            String area = Objects.toString(getCellValue(row, 0), null);
            String mode = Objects.toString(getCellValue(row, 1), null);
            if (area != null) {
                modes.add(AdequacyModeEntity.builder()
                        .area(area)
                        .mode(mode)
                        .trajectory(trajectory)
                        .build());
            }
        }
        return modes;
    }

    private List<AdequacySettingsEntity> buildAdequacySettingsList(Workbook workbook, TrajectoryEntity trajectory, List<String> missingTabs) {
        Sheet sheet = workbook.getSheet(SHEET_SETTINGS);
        if (sheet == null) {
            missingTabs.add(SHEET_SETTINGS);
            return Collections.emptyList();
        }
        AdequacySettingsEntity settingsEntity = new AdequacySettingsEntity();
        settingsEntity.setTrajectory(trajectory);
        boolean isSettingEmpty = true;
        
        for (Row row : sheet) {
            if (isRowEmpty(row)) continue;
            String key = Objects.toString(getCellValue(row, 0), "").toLowerCase().replace("-", "_");
            Object value = getCellValue(row, 1);

            BiConsumer<AdequacySettingsEntity, Object> setter = settingsSetters.get(key);
            if (setter != null && value != null) {
                try {
                    isSettingEmpty = false;
                    setter.accept(settingsEntity, value);
                } catch (Exception e) {
                    log.warn("Could not set setting {} with value {}: {}", key, value, e.getMessage());
                }
            }
        }
        if (isSettingEmpty) {
            return Collections.emptyList();
        } else {
            return Collections.singletonList(settingsEntity);
        }
    }

    private Path findDefaultFile(Path trajectoryFilePath, String trajectoryToUse) throws BusinessException, IOException {
        Path defaultFile;

        try (Stream<Path> stream = Files.list(trajectoryFilePath)) {
            defaultFile = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> FILE_NAME_DEFAULT.equalsIgnoreCase(
                            p.getFileName().toString()))
                    .findFirst()
                    .orElseThrow(() ->
                            BusinessException.builder()
                                    .errorMessageArguments(List.of(trajectoryToUse))
                                    .message("Missing file default in AdequacyPatch trajectory {0}")
                                    .httpStatus(HttpStatus.BAD_REQUEST)
                                    .build());
        }
        
        return defaultFile;
    }
    
    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        Cell cell = row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return cell == null || cell.getCellType() == CellType.BLANK;
    }
}
