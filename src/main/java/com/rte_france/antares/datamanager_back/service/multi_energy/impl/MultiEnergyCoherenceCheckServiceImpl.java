package com.rte_france.antares.datamanager_back.service.multi_energy.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.LoadEntity;
import com.rte_france.antares.datamanager_back.repository.model.StStorageEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.multi_energy.MultiEnergyCoherenceCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.rte_france.antares.datamanager_back.util.Utils.extractAreaNamesFromAreaMe;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiEnergyCoherenceCheckServiceImpl implements MultiEnergyCoherenceCheckService {
    private final TrajectoryRepository trajectoryRepository;

    @Override
    public void checkAreaMETrajectoryConsistency(Integer studyId, String trajectoryType, TrajectoryEntity trajectory) {
        if (studyId == null || trajectoryType == null || trajectory == null) {
            return;
        }

        List<TrajectoryEntity> areaMeTrajectories = trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA_ME.name(), studyId);
        Set<String> aresMeList = extractAreaNamesFromAreaMe(areaMeTrajectories);
        
        Set<String> nodeList = getNodeListFromTrajectory(trajectoryType, trajectory);
        Set<String> missingAreas = aresMeList.stream()
                .filter(area -> !nodeList.contains(area))
                .collect(Collectors.toSet());

        if (!missingAreas.isEmpty()) {
            throw createValidationError(List.of(String.join(", ", missingAreas), trajectoryType));
        }
    }

    private BusinessException createValidationError(List<String> args) {
        return BusinessException.builder()
                .message("Areas {0} from AREA_ME trajectory is (are) not present in {1} trajectory")
                .errorMessageArguments(args)
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build();
    }

    private <T> Set<String> extractAreas(Collection<T> entities, Function<T, String> areaExtractor) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptySet();
        }

        return entities.stream()
                .map(areaExtractor)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(area -> !area.isEmpty())
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
    }

    private Set<String> getNodeListFromTrajectory(String trajectoryType, TrajectoryEntity trajectory) {
        if (trajectory == null || trajectoryType == null) {
            return Collections.emptySet();
        }

        return switch (TrajectoryType.valueOf(trajectoryType)) {
            case LOAD_ME -> extractAreas(trajectory.getLoadEntities(), LoadEntity::getArea);
            case STS_ME  -> extractAreas(trajectory.getStStorageEntities(), StStorageEntity::getArea);
            default      -> Collections.emptySet();
        };
    }
}
