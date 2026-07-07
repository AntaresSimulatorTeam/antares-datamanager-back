package com.rte_france.antares.datamanager_back.service.adequacy;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.AdequacyModeRepository;
import com.rte_france.antares.datamanager_back.repository.AdequacySettingsRepository;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.AdequacyModeEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.settings.AdequacySettingsEntity;
import com.rte_france.antares.datamanager_back.repository.model.settings.PriceTakingOrderEnum;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.user.UserService;
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
import java.util.function.BiConsumer;

import static com.rte_france.antares.datamanager_back.util.Utils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdequacyFileProcessorServiceImpl implements AdequacyFileProcessorService {

    private final TrajectoryRepository trajectoryRepository;
    private final TrajectoryServiceImpl trajectoryService;
    private final UserService userService;
    private final AdequacyModeRepository adequacyModeRepository;
    private final AdequacySettingsRepository adequacySettingsRepository;
    private final StudyRepository studyRepository;

    private static final Map<String, BiConsumer<AdequacySettingsEntity, Object>> settingsSetters = new HashMap<>();

    static {
        settingsSetters.put("include_adq_patch", (e, v) -> e.setIncludeAdqPatch((Boolean) v));
        settingsSetters.put("set_to_null_ntc_from_physical_out_to_physical_in_for_first_step",
                (e, v) -> e.setSetToNullNtcFromPhysicalOutToPhysicalInForFirstStep((Boolean) v));
        settingsSetters.put("price_taking_order",
                (e, v) -> e.setPriceTakingOrder(PriceTakingOrderEnum.valueOf(v.toString())));
        settingsSetters.put("include_hurdle_cost_csr", (e, v) -> e.setIncludeHurdleCostCsr((Boolean) v));
        settingsSetters.put("check_csr_cost_function", (e, v) -> e.setCheckCsrCostFunction((Boolean) v));
        settingsSetters.put("threshold_initiate_curtailment_sharing_rule", (e, v) -> e.setThresholdInitiateCurtailmentSharingRule(castToInteger(v)));
        settingsSetters.put("threshold_display_local_matching_rule_violations", (e, v) -> e.setThresholdDisplayLocalMatchingRuleViolations(castToInteger(v)));
        settingsSetters.put("threshold_csr_variable_bounds_relaxation", (e, v) -> e.setThresholdCsrVariableBoundsRelaxation(castToInteger(v)));
        settingsSetters.put("threshold_csr_variable_bounds_relaxation_for_first_step", (e, v) -> e.setThresholdCsrVariableBoundsRelaxationForFirstStep(castToInteger(v)));
        settingsSetters.put("enable_first_step", (e, v) -> e.setEnableFirstStep((Boolean) v));
        settingsSetters.put("set_to_null_ntc_between_physical_out_for_first_step",
                (e, v) -> e.setSetToNullNtcBetweenPhysicalOutForFirstStep((Boolean) v));
        settingsSetters.put("redispatch", (e, v) -> e.setRedispatch((Boolean) v));
    }

    private static Integer castToInteger(Object v) {
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        return null;
    }

    @Override
    @Transactional
    public TrajectoryEntity processAdequacyFile(String trajectoryToUse, String horizon, Integer studyId, boolean isCivilYear) throws IOException {

        Path path = trajectoryService.getTrajectoryFilePath(TrajectoryType.ADEQUACY_PATCH, trajectoryToUse, null);

        String fileName = getFileNameWithoutExtensionAndWithoutPrefix(path.getFileName().toString(), TrajectoryType.ADEQUACY_PATCH.name());
        Optional<TrajectoryEntity> trajectoryEntity = trajectoryRepository.
                findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(fileName, horizon, TrajectoryType.ADEQUACY_PATCH.name());

        String createdBy = Optional.ofNullable(userService.getCurrentUserDetails())
                .map(UserInfoDto::getNni)
                .orElse("UNKNOWN_USER");

        int version = trajectoryEntity.map(TrajectoryEntity::getVersion).orElse(0);

        if (trajectoryEntity.isPresent() && checkTrajectoryVersion(path, trajectoryEntity.get())) {
            version = trajectoryEntity.get().getVersion();
        }

        TrajectoryEntity trajectory = buildTrajectory(path, version, horizon, createdBy,
                TrajectoryType.ADEQUACY_PATCH, null, null, null);

        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            List<AdequacyModeEntity> modes = buildAdequacyModeList(workbook, trajectory);
            List<AdequacySettingsEntity> settings = buildAdequacySettingsList(workbook, trajectory);

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
            throw TechnicalException.builder().message("Could not process adequacy file: " + e.getMessage()).build();
        }
    }

    private List<AdequacyModeEntity> buildAdequacyModeList(Workbook workbook, TrajectoryEntity trajectory) {
        Sheet sheet = workbook.getSheet("perimetre");
        if (sheet == null) {
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

    private List<AdequacySettingsEntity> buildAdequacySettingsList(Workbook workbook, TrajectoryEntity trajectory) {
        Sheet sheet = workbook.getSheet("settings");
        if (sheet == null) {
            return Collections.emptyList();
        }
        AdequacySettingsEntity settingsEntity = new AdequacySettingsEntity();
        settingsEntity.setTrajectory(trajectory);

        for (Row row : sheet) {
            if (isRowEmpty(row)) continue;
            String key = Objects.toString(getCellValue(row, 0), "").toLowerCase().replace("-", "_");
            Object value = getCellValue(row, 1);

            BiConsumer<AdequacySettingsEntity, Object> setter = settingsSetters.get(key);
            if (setter != null && value != null) {
                try {
                    setter.accept(settingsEntity, value);
                } catch (Exception e) {
                    log.warn("Could not set setting {} with value {}: {}", key, value, e.getMessage());
                }
            }
        }
        return Collections.singletonList(settingsEntity);
    }


    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        Cell cell = row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return cell == null || cell.getCellType() == CellType.BLANK;
    }
}
