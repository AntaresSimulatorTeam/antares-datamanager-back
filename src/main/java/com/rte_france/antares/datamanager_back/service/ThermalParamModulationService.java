package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

public interface ThermalParamModulationService {

    TrajectoryEntity saveParamModulationToDb(String trajectoryToUse, String horizon);
}
