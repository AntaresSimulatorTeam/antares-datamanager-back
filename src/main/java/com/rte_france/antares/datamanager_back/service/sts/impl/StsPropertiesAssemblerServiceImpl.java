package com.rte_france.antares.datamanager_back.service.sts.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.StsGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.mapper.StStorageMapper;
import com.rte_france.antares.datamanager_back.repository.model.StStorageEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.sts.StsGenerationAssemblerService;
import com.rte_france.antares.datamanager_back.service.sts.StsTsFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StsPropertiesAssemblerServiceImpl implements StsGenerationAssemblerService {


    private final AntaresDataManagerProperties antaresDataManagerProperties;
    private final NasFileService nasFileService;


    @Override
    public Map<String, StsGenerationDTO> assembleStsProperties(StudyEntity studyEntity) {
        return studyEntity.getTrajectories().stream()
                .filter(Objects::nonNull)
                .filter(t -> TrajectoryType.STS.equals(TrajectoryType.valueOf(t.getType())))
                .map(TrajectoryEntity::getStStorageEntities)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(sts -> {
                    double injection = sts.getInjection() != null ? sts.getInjection().doubleValue() : 0.0;
                    double withdrawal = sts.getWithdrawal() != null ? sts.getWithdrawal().doubleValue() : 0.0;
                    double storage = sts.getStorage() != null ? sts.getStorage().doubleValue() : 0.0;
                    return (injection + withdrawal + storage) > 0;
                })
                .collect(Collectors.toMap(
                        sts -> sts.getArea().toUpperCase() + "_" + sts.getName(),
                        sts -> {
                            StsGenerationDTO dto = StStorageMapper.mapToStsGenerationDTO(sts);
                            // Ensure sts_ts is populated from created matrices (could be empty)
                            dto.setStsTsList(createMatrixStsTsFiles(sts, studyEntity.getHorizon()));
                            return dto;
                        },
                        (existing, replacement) -> existing
                ));
    }

    @Override
    public List<String> createMatrixStsTsFiles(StStorageEntity stsEntity, String horizon) {
        if (stsEntity == null || stsEntity.getTsPath() == null || stsEntity.getTsPath().isBlank()) {
            return Collections.emptyList();
        }
        Path tsDir = Path.of(stsEntity.getTsPath());

        try {
            String outputDir = antaresDataManagerProperties.getStsTsOutputDirectory();
            List<String> saved = new ArrayList<>();
            StsTsFile[] requiredFiles = Arrays.stream(StsTsFile.values())
                    .filter(e -> e != StsTsFile.ADDITIONAL_CONSTRAINTS)
                    .toArray(StsTsFile[]::new);
            for (StsTsFile stsTsFile : requiredFiles) {
                Path inputPath = stsTsFile.resolve(tsDir);

                if (!java.nio.file.Files.exists(inputPath)) {
                    throw BusinessException.builder()
                            .message("Required STS series file not found: {0}")
                            .errorMessageArguments(List.of(inputPath.toString()))
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build();
                }

                try {
                    saved.add(nasFileService.saveMatrixToNas(inputPath, outputDir, horizon));
                } catch (IllegalArgumentException e) {
                    throw BusinessException.builder()
                            .message(e.getMessage())
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build();
                }
            }

            return saved;

        } catch (IOException e) {
            throw TechnicalException.builder()
                    .message(e.getMessage())
                    .cause(e)
                    .build();
        }
    }


}