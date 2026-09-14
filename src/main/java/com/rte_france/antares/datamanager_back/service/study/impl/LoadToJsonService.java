package com.rte_france.antares.datamanager_back.service.study.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.model.LoadEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoadToJsonService {

    private static final Pattern DEFAULT_LOAD_PATTERN = Pattern.compile("_(.*?)[_\\.]");
    private static final Pattern LOAD_ME_PATTERN = Pattern.compile("load_([a-zA-Z0-9_]+)_[0-9]{4}-[0-9]{4}\\.csv", Pattern.CASE_INSENSITIVE);

    private final NasFileService nasFileService;

    private final AntaresDataManagerProperties antaresDataManagerProperties;

    public Map<String, List<String>> getListArrowLoadFilesByAreaFromStudy(StudyEntity studyEntity) {
        log.info("Retrieve LOAD files for study = {}", studyEntity.getId());
        Map<Integer, String> arrowFileCache = new HashMap<>();
        Map<String, List<String>> result = studyEntity.getTrajectories().stream()
                .filter(this::isLoadTrajectoryWithEntities)
                .flatMap(trajectory -> processTrajectoryLoads(trajectory, studyEntity.getId(), DEFAULT_LOAD_PATTERN, arrowFileCache))
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                ));
        log.info("Number of LOAD files by zone {}", result.size());
        return result;
    }

    public Map<String, List<String>> getListArrowLoadMeFilesFromStudy(StudyEntity studyEntity) {
        log.info("Retrieve LOAD_ME files for study = {}", studyEntity.getId());
        Map<Integer, String> arrowFileCache = new HashMap<>();
        Map<String, List<String>> result = studyEntity.getTrajectories().stream()
                .filter(this::isLoadMeTrajectoryWithEntities)
                .flatMap(trajectory -> processTrajectoryLoadMe(trajectory, arrowFileCache))
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                ));
        log.info("Number of LOAD_ME files by zone {}", result.size());
        return result;
    }

    private boolean isLoadTrajectoryWithEntities(TrajectoryEntity trajectory) {
        return TrajectoryType.LOAD.name().equals(trajectory.getType())
                && trajectory.getLoadEntities() != null
                && !trajectory.getLoadEntities().isEmpty();
    }

    private boolean isLoadMeTrajectoryWithEntities(TrajectoryEntity trajectory) {
        return TrajectoryType.LOAD_ME.name().equals(trajectory.getType())
                && trajectory.getLoadEntities() != null
                && !trajectory.getLoadEntities().isEmpty();
    }

    private Stream<Map.Entry<String, String>> processTrajectoryLoads(TrajectoryEntity trajectory, Integer studyId, Pattern pattern,
                                                                      Map<Integer, String> arrowFileCache) {
        log.info("Load processing for trajectory= {} area={}", trajectory.getFileName(), trajectory.getArea());
        if ("OTHERS".equals(trajectory.getArea())) {
            return trajectory.getLoadEntities().stream()
                    .filter(loadEntity -> isLoadLinkedToStudy(loadEntity, studyId))
                    .map(loadEntity -> processLoadEntityWithPattern(loadEntity, trajectory, pattern, arrowFileCache));
        } else {
            return trajectory.getLoadEntities().stream()
                    .map(loadEntity -> Map.entry(
                            trajectory.getArea().toUpperCase(Locale.ROOT),
                            resolveOutputFileName(loadEntity, trajectory, arrowFileCache)));
        }
    }

    private Stream<Map.Entry<String, String>> processTrajectoryLoadMe(TrajectoryEntity trajectory,
                                                                      Map<Integer, String> arrowFileCache) {
        log.info("Load_ME processing for trajectory= {}", trajectory.getFileName());
        return trajectory.getLoadEntities().stream()
                .map(loadEntity -> {
                    String area = resolveLoadMeArea(loadEntity);
                    return Map.entry(
                            area.toUpperCase(Locale.ROOT),
                            resolveOutputFileName(loadEntity, trajectory, arrowFileCache));
                });
    }

    private String resolveLoadMeArea(LoadEntity loadEntity) {
        if (StringUtils.isNotBlank(loadEntity.getArea())) {
            return loadEntity.getArea();
        }
        return extractAreaFromFileName(loadEntity.getFileName(), LOAD_ME_PATTERN);
    }

    private  Map.Entry<String, String> processLoadEntityWithPattern(LoadEntity loadEntity, TrajectoryEntity trajectory, Pattern pattern,
                                                                      Map<Integer, String> arrowFileCache) {
        String outputFileName = resolveOutputFileName(loadEntity, trajectory, arrowFileCache);
        String area = extractAreaFromFileName(outputFileName, pattern);
        return Map.entry(area, outputFileName);
    }

    private  String resolveOutputFileName(LoadEntity loadEntity, TrajectoryEntity trajectory, Map<Integer, String> arrowFileCache) {
        return arrowFileCache.computeIfAbsent(loadEntity.getId(), id -> generateAndSaveOutputFileName(loadEntity, trajectory));
    }


    private  boolean isLoadLinkedToStudy(LoadEntity loadEntity, int studyId) {
        return loadEntity.getTrajectoryEntities().stream()
                .flatMap(traj -> traj.getScenarioEntities().stream())
                .anyMatch(study -> study.getId().equals(studyId));
    }

    private  String generateAndSaveOutputFileName(LoadEntity loadEntity, TrajectoryEntity trajectory) {
        String outputLoadDir = antaresDataManagerProperties.getOutputLoadDirectory();
        String loadDir = TrajectoryType.LOAD_ME.name().equals(trajectory.getType())
                ? antaresDataManagerProperties.getLoadMeDirectory()
                : antaresDataManagerProperties.getLoadDirectory();
        var inputTxtFilePath = Paths.get(
                antaresDataManagerProperties.getNasDirectory(),
                antaresDataManagerProperties.getTrajectoryFilePath(),
                loadDir,
                trajectory.getFileName(),
                loadEntity.getFileName()
        ).normalize();

        try {
            String saved = nasFileService.readAndSaveMatrixToNas(inputTxtFilePath, outputLoadDir, null, false);
            log.info("Matrix saved to NAS for input {} -> {}", inputTxtFilePath, saved);
            return saved;
        } catch (IOException e) {
            log.error("Erreur lors de la sauvegarde du matrix pour {} : {}", inputTxtFilePath, e.getMessage());
            throw TechnicalException.builder().message(e.getMessage()).cause(e).build();
        }
    }

    private  String extractAreaFromFileName(String fileName, Pattern pattern) {
        var matcher = pattern.matcher(fileName);
        String area = matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : "OTHERS";
        log.info("Extraction de la zone à partir du nom de fichier '{}': {}", fileName, area);
        return area;
    }
}
