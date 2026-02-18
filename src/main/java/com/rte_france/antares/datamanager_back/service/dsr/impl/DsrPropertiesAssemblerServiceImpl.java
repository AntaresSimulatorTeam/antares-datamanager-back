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
import java.math.BigDecimal;
import java.nio.file.Files;
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

    // Compute modulation files only once
    List<String> modulationFiles = createMatrixDsrTsFiles(studyEntity);

    return studyEntity.getTrajectories().stream()
            .filter(Objects::nonNull)
            .filter(t -> TrajectoryType.DSR.name().equals(t.getType()))
            .map(TrajectoryEntity::getDsrClusterEntities)
            .filter(Objects::nonNull)
            .flatMap(Collection::stream)
            .filter(Objects::nonNull)
            .filter(dsr ->
                    Boolean.TRUE.equals(dsr.getToUse()) &&
                            dsr.getCapacity() != null &&
                            dsr.getCapacity().compareTo(BigDecimal.ZERO) != 0
            )
            .collect(Collectors.toMap(
                    dsr -> buildDsrKey(dsr),
                    dsr -> {
                        DsrGenerationDTO dto = DsrMapper.mapToDsrGenerationDTO(dsr);
                        if (Boolean.TRUE.equals(dsr.getModulation())) {
                            dto.setDsrTsList(modulationFiles);
                        }
                        return dto;
                    },
                    (existing, replacement) -> existing
            ));
}

    private String buildDsrKey(DsrClusterEntity dsr) {
        String area = dsr.getArea() != null ? dsr.getArea().toUpperCase() : "";
        String name = dsr.getName() != null ? dsr.getName() : "";
        return area + "_" + name;
    }

    public List<String> createMatrixDsrTsFiles(StudyEntity studyEntity) {

        List<DsrCapacityModulationEntity> modulations =
                studyEntity.getTrajectories().stream()
                        .filter(Objects::nonNull)
                        .filter(t -> TrajectoryType.DSR_CAPACITY_MODULATION.name().equals(t.getType()))
                        .map(TrajectoryEntity::getDsrCapacityModulationEntities)
                        .filter(Objects::nonNull)
                        .flatMap(Collection::stream)
                        .filter(Objects::nonNull)
                        .toList();

        if (modulations.isEmpty()) {
            return Collections.emptyList();
        }

        Path dsrCapacityDir = Path.of(antaressDataManagerProperties.getNasDirectory())
                .resolve(antaressDataManagerProperties.getTrajectoryFilePath())
                .resolve(antaressDataManagerProperties.getDsrCapacityDirectory());

        String outputDir = antaressDataManagerProperties.getDsrModulationTsOutputDirectory();

        Set<String> savedFiles = new LinkedHashSet<>();

        // Avoid reprocessing same tsName + checksum
        Map<String, String> processedTs = new HashMap<>();

        for (DsrCapacityModulationEntity modulation : modulations) {

            String tsName = modulation.getTsName();
            if (tsName == null || tsName.isBlank()) {
                continue;
            }

            String checksum = Optional.ofNullable(modulation.getChecksum()).orElse("");
            String cacheKey = tsName + "_" + checksum;

            // Already processed → reuse
            if (processedTs.containsKey(cacheKey)) {
                savedFiles.add(processedTs.get(cacheKey));
                continue;
            }

            Path inputPath = dsrCapacityDir.resolve(tsName);

            if (!Files.exists(inputPath)) {
                throw BusinessException.builder()
                        .message("Required DSR capacity modulation series file not found: {0}")
                        .errorMessageArguments(List.of(inputPath.toString()))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            try {
                String savedFilename = nasFileService.saveMatrixToNas(inputPath, outputDir);
                processedTs.put(cacheKey, savedFilename);
                savedFiles.add(savedFilename);

            } catch (IOException e) {
                throw TechnicalException.builder()
                        .message("Failed to save DSR arrow modulation file: " + tsName)
                        .cause(e)
                        .build();

            } catch (IllegalArgumentException e) {
                throw BusinessException.builder()
                        .message(e.getMessage())
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
        }

        return new ArrayList<>(savedFiles);
    }


    @Override
    public List<String> createMatrixDsrTsFiles(DsrClusterEntity dsrClusterEntity, String horizon) {
        // No series for the cluster returns an empty list
        return Collections.emptyList();
    }


}