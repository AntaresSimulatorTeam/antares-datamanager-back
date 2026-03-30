package com.rte_france.antares.datamanager_back.service.sts;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

public interface StStorageFileProcessorService {

     TrajectoryEntity processStStorageFile(String trajectoryToUse, String horizon, Integer studyId, boolean isCivilYear, String area, String technology) throws Exception;
     }
