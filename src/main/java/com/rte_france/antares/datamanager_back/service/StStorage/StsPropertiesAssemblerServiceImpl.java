package com.rte_france.antares.datamanager_back.service.StStorage;

import com.rte_france.antares.datamanager_back.dto.StsGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.mapper.StStorageMapper;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StsPropertiesAssemblerServiceImpl implements StsPropertiesAssemblerService {

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
                        StStorageMapper::mapToStsGenerationDTO,
                        (existing, replacement) -> existing
                ));
    }
}
