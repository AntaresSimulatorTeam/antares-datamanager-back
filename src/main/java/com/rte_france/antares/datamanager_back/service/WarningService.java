package com.rte_france.antares.datamanager_back.service;


import com.rte_france.antares.datamanager_back.dto.WarningDTO;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.WarningCode;
import com.rte_france.antares.datamanager_back.repository.model.WarningMessageEntity;

import java.util.List;
import java.util.Set;

public interface WarningService {

    String getMessage(String code, Object... args);

    String getNotFoundMessage();

    void acknowledgeWarning(Integer id);

    void addWarning(Set<WarningMessageEntity> warningMessages,
                    List<String> warnings,
                    WarningCode warningCode,
                    Integer studyId,
                    String userNni,
                    TrajectoryEntity trajectory);

    List<WarningDTO> getWarningsForTrajectory(Integer trajectoryId, Integer studyId);

    }
