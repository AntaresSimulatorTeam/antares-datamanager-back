package com.rte_france.antares.datamanager_back.service.area_link;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

/**
 * Service for LINK_ME trajectory coherence validation.
 * Validates that areas referenced in LINK_ME trajectories exist in AREA/AREA_ME trajectories.
 */
public interface LinkMeCoherenceCheckService {

    /**
     * Validates LINK_ME trajectory coherence against AREA and AREA_ME trajectories.
     *
     * RG1: All nodeFrom areas must exist in either AREA or AREA_ME trajectory
     * RG2: All nodeTo areas must exist in AREA_ME trajectory
     *
     * @param studyId the ID of the study
     * @param trajectory the LINK_ME trajectory to validate
     * @throws BusinessException if validation fails with appropriate error message
     */
    void validateLinkMeCoherence(Integer studyId, TrajectoryEntity trajectory) throws BusinessException;
}
