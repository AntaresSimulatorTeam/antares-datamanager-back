package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.ThermalModulationParameterRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.ThermalModulationParameterEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.WarningMessageEntity;
import com.rte_france.antares.datamanager_back.service.ThermalModulationParamService;
import com.rte_france.antares.datamanager_back.util.PathSecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;


@Slf4j
@Service
@RequiredArgsConstructor
public class ThermalModulationParamServiceImpl implements ThermalModulationParamService {

    private final TrajectoryRepository trajectoryRepository;
    private final ThermalModulationParameterRepository modulationParameterRepository;
    private final UserService userService;
    private final PathSecurityUtil pathSecurityUtil;

    @Override
    public TrajectoryEntity saveParamModulationToDb(String trajectoryToUse, String horizon, Integer studyId) {
        Path modulationDirectory = pathSecurityUtil.buildTrajectoryPath(
                trajectoryToUse, TrajectoryType.THERMAL_TECHNICAL_MODULATION_PARAMETER);

        if (!Files.exists(modulationDirectory)) {
            throw TechnicalException.builder()
                    .message("Thermal modulation CSV not found: " + modulationDirectory)
                    .build();
        }

        Optional<TrajectoryEntity> existingOpt = trajectoryRepository
                .findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                        trajectoryToUse,
                        horizon,
                        TrajectoryType.THERMAL_TECHNICAL_MODULATION_PARAMETER.name()
                );

        String createdBy = Optional.ofNullable(userService.getCurrentUserDetails())
                .map(UserInfoDto::getNni)
                .orElse("UNKNOWN__USER");

        try {
            int nextVersion = existingOpt.map(e -> e.getVersion() + 1).orElse(1);

            TrajectoryEntity trajectory = TrajectoryEntity.builder()
                    .fileName(trajectoryToUse)
                    .fileSize(Files.size(modulationDirectory))
                    .checksum("NA")
                    .type(TrajectoryType.THERMAL_TECHNICAL_MODULATION_PARAMETER.name())
                    .version(nextVersion)
                    .createdBy(createdBy)
                    .creationDate(LocalDateTime.now())
                    .lastModificationContentDate(LocalDateTime.now())
                    .horizon(horizon)
                    .build();

            return buildAndSaveModulationParamTrajectory(horizon, trajectoryToUse, modulationDirectory, trajectory, studyId, null);
        } catch (IOException e) {
            throw new RuntimeException("Error reading modulation file size", e);
        }
    }

    @Override
    public TrajectoryEntity buildAndSaveModulationParamTrajectory(
            String horizon,
            String trajectoryName,
            Path trajectoryPath,
            TrajectoryEntity trajectory,
            Integer studyId,
            Set<WarningMessageEntity> warningMessageEntities
    ) {
        List<String> paramFiles = getValidModulationParamFiles(trajectoryPath);

        if (paramFiles.isEmpty()) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(trajectoryName, horizon))
                    .message("No valid param modulation files found in the trajectory: {0} and horizon: {1}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        Set<ThermalModulationParameterEntity> modulationParamEntities = new HashSet<>();

        for (String paramFileName : paramFiles) {

            Optional<ThermalModulationParameterEntity> existingModulationEntity =
                    modulationParameterRepository.findByFileNameAndTrajectoryFileName(
                            paramFileName,
                            trajectory.getFileName()
                    );

            ThermalModulationParameterEntity modulationParameterEntity = existingModulationEntity.orElseGet(() ->
                    ThermalModulationParameterEntity.builder()
                            .tsName(paramFileName)
                            .build()
            );

            modulationParameterEntity.addTrajectoryEntity(trajectory);
            modulationParamEntities.add(modulationParameterEntity);
        }

        trajectory.setThermalModulationParams(modulationParamEntities);
        trajectory.setWarningMessages(warningMessageEntities);

        return trajectoryRepository.save(trajectory);
    }


    private List<String> getValidModulationParamFiles(Path dir) {
        List<String> paramFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.csv")) {
            for (Path file : stream) {
                paramFiles.add(file.getFileName().toString());
            }
        } catch (IOException e) {
            System.err.println("Error reading files: " + dir);
        }
        return paramFiles;
    }


}
