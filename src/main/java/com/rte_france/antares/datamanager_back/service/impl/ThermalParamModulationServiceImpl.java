package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.ThermalParamModulationService;
import com.rte_france.antares.datamanager_back.util.PathSecurityUtil;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrix;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrixColumn;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThermalParamModulationServiceImpl implements ThermalParamModulationService {

    private final TrajectoryRepository trajectoryRepository;
    private final TimeSeriesWriter timeSeriesWriter;
    private final NasFileService nasFileService;
    private final UserService userService;
    private final PathSecurityUtil pathSecurityUtil;

    @Override
    public TrajectoryEntity saveParamModulationToDb(String trajectoryToUse, String horizon) {
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

            return trajectoryRepository.save(trajectory);
        } catch (IOException e) {
            throw new RuntimeException("Error reading modulation file size", e);
        }
    }


}
