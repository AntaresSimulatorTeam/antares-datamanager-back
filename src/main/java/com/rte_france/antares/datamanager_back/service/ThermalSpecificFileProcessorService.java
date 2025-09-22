package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.repository.model.ThermalSpecificParametersEntity;

import java.nio.file.Path;
import java.util.List;

public interface ThermalSpecificFileProcessorService {

    List<ThermalSpecificParametersEntity> buildThermalSpecificParameterValueList(String trajectoryName, Path trajectoryFilePath, String horizon,String area, Integer studyId);
}
