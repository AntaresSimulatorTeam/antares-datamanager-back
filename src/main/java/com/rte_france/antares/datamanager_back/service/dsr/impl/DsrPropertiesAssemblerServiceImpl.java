package com.rte_france.antares.datamanager_back.service.dsr.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.DsrGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.mapper.DsrMapper;
import com.rte_france.antares.datamanager_back.repository.model.DsrCapacityModulationEntity;
import com.rte_france.antares.datamanager_back.repository.model.DsrClusterEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.dsr.DsrGenerationAssemblerService;
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
public class DsrPropertiesAssemblerServiceImpl implements DsrGenerationAssemblerService {


    private final AntaressDataManagerProperties antaressDataManagerProperties;
    private final NasFileService nasFileService;


    @Override
    public Map<String, DsrGenerationDTO> assembleDsrProperties(StudyEntity studyEntity) {
        return studyEntity.getTrajectories().stream()
                .filter(Objects::nonNull)
                .filter(t -> TrajectoryType.DSR.equals(TrajectoryType.valueOf(t.getType())))
                .map(TrajectoryEntity::getDsrClusterEntities)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(
                        dsr -> dsr.getArea().toUpperCase() + "_" + dsr.getName(),
                        dsr -> {
                            DsrGenerationDTO dto = DsrMapper.mapToDsrGenerationDTO(dsr);
                            dto.setDsrTsList(createMatrixDsrTsFiles(studyEntity));
                            return dto;
                        },
                        (existing, replacement) -> existing
                ));
    }
    public List<String> createMatrixDsrTsFiles(StudyEntity studyEntity) {
        // Parcourir les trajectoires du scénario et collecter les tsName des entités DSR capacity modulation
        List<String> tsNames = studyEntity.getTrajectories().stream()
                .filter(Objects::nonNull)
                .filter(t -> {
                    try {
                        return TrajectoryType.DSR_CAPACITY_MODULATION.equals(TrajectoryType.valueOf(t.getType()));
                    } catch (Exception e) {
                        return false;
                    }
                })
                .map(TrajectoryEntity::getDsrCapacityModulations)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(DsrCapacityModulationEntity::getTsName)
                .filter(Objects::nonNull)
                .filter(name -> !name.isBlank())
                .distinct()
                .toList();

        if (tsNames.isEmpty()) {
            return Collections.emptyList();
        }

        String dsrDir = antaressDataManagerProperties.getDsrCapacityDirectory();
        String outputDir = "output/dsr_arrow";

        List<String> saved = new ArrayList<>();
        for (String tsName : tsNames) {
            Path inputPath = Path.of(dsrDir).resolve(tsName);
            if (!java.nio.file.Files.exists(inputPath)) {
                throw BusinessException.builder()
                        .message("Required DSR capacity modulation series file not found: {0}")
                        .errorMessageArguments(List.of(inputPath.toString()))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
            try {
                saved.add(nasFileService.saveMatrixToNas(inputPath, outputDir));
            } catch (IOException e) {
                throw TechnicalException.builder()
                        .message(e.getMessage())
                        .cause(e)
                        .build();
            } catch (IllegalArgumentException e) {
                throw BusinessException.builder()
                        .message(e.getMessage())
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
        }
        return saved;
    }

    @Override
    public List<String> createMatrixDsrTsFiles(DsrClusterEntity dsrClusterEntity, String horizon) {
        // Pas de séries par cluster directement; retourner vide pour compatibilité
        return Collections.emptyList();
    }


}