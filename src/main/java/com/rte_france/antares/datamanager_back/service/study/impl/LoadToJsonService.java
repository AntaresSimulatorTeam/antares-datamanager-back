package com.rte_france.antares.datamanager_back.service.study.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.LoadRepository;
import com.rte_france.antares.datamanager_back.repository.model.LoadEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoadToJsonService {

    private final LoadRepository loadRepository;

    private final NasFileService nasFileService;

    private final AntaresDataManagerProperties antaresDataManagerProperties;

    public  Map<String, List<String>> getListArrowLoadFilesByAreaFromStudy(StudyEntity studyEntity) {
        log.info("Retrieve LOAD files for study = {}", studyEntity.getId());
        Pattern pattern = Pattern.compile("_(.*?)[_\\.]");
        Map<String, List<String>> result = studyEntity.getTrajectories().stream()
                .filter(this::isLoadTrajectoryWithEntities)
                .flatMap(trajectory -> processTrajectoryLoads(trajectory, studyEntity.getId(), pattern))
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                ));
        log.info("Number of LOAD files by zone {}", result.size());
        return result;
    }

    private boolean isLoadTrajectoryWithEntities(TrajectoryEntity trajectory) {
        return "LOAD".equals(trajectory.getType())
                && trajectory.getLoadEntities() != null
                && !trajectory.getLoadEntities().isEmpty();
    }

    private  Stream<Map.Entry<String, String>> processTrajectoryLoads(TrajectoryEntity trajectory, Integer studyId, Pattern pattern) {
        log.info("Load processing for trajectory= {} area={}", trajectory.getFileName(), trajectory.getArea());
        if ("OTHERS".equals(trajectory.getArea())) {
            return trajectory.getLoadEntities().stream()
                    .filter(loadEntity -> isLoadLinkedToStudy(loadEntity, studyId))
                    .map(loadEntity -> processLoadEntityWithPattern(loadEntity, trajectory, pattern));
        } else {
            return trajectory.getLoadEntities().stream()
                    .map(loadEntity -> {
                        processLoadEntityWithPattern(loadEntity, trajectory, pattern);
                        return Map.entry(trajectory.getArea().toUpperCase(), loadEntity.getOutPutFileName());
                    });

        }
    }
    private  Map.Entry<String, String> processLoadEntityWithPattern(LoadEntity loadEntity, TrajectoryEntity trajectory, Pattern pattern) {
        if (loadEntity.getOutPutFileName() == null) {
            log.info("No outPutFileName for load {} - génération en cours", loadEntity.getFileName());
            String outputFileName = generateAndSaveOutputFileName(loadEntity, trajectory);
            loadEntity.setOutPutFileName(outputFileName);
            loadRepository.save(loadEntity);
            log.info("OutPutFileName généré et sauvegardé pour load {} : {}", loadEntity.getFileName(), outputFileName);
        } else {
            log.info("OutPutFileName existant pour load {} : {}", loadEntity.getFileName(), loadEntity.getOutPutFileName());
        }
        String area = extractAreaFromFileName(loadEntity.getOutPutFileName(), pattern);
        return Map.entry(area, loadEntity.getOutPutFileName());
    }


    private  boolean isLoadLinkedToStudy(LoadEntity loadEntity, int studyId) {
        return loadEntity.getTrajectoryEntities().stream()
                .flatMap(traj -> traj.getScenarioEntities().stream())
                .anyMatch(study -> study.getId().equals(studyId));
    }

    private  String generateAndSaveOutputFileName(LoadEntity loadEntity, TrajectoryEntity trajectory) {
        String outputLoadDir = antaresDataManagerProperties.getOutputLoadDirectory();
        var inputTxtFilePath = Paths.get(
                antaresDataManagerProperties.getNasDirectory(),
                antaresDataManagerProperties.getTrajectoryFilePath(),
                antaresDataManagerProperties.getLoadDirectory(),
                trajectory.getFileName(),
                loadEntity.getFileName()
        ).normalize();

        try {
            String saved = nasFileService.saveMatrixToNas(inputTxtFilePath, outputLoadDir);
            log.info("Matrix saved to NAS for input {} -> {}", inputTxtFilePath, saved);
            return saved;
        } catch (IOException e) {
            log.error("Erreur lors de la sauvegarde du matrix pour {} : {}", inputTxtFilePath, e.getMessage());
            throw TechnicalException.builder().message(e.getMessage()).cause(e).build();
        }
    }

    private  String extractAreaFromFileName(String fileName, Pattern pattern) {
        var matcher = pattern.matcher(fileName);
        String area = matcher.find() ? matcher.group(1).toUpperCase() : "OTHERS";
        log.info("Extraction de la zone à partir du nom de fichier '{}': {}", fileName, area);
        return area;
    }
}
