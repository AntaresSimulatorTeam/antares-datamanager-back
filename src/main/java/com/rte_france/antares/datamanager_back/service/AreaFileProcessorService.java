package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.AreaConfigEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.io.File;
import java.io.IOException;
import java.util.List;

public interface AreaFileProcessorService {

     TrajectoryEntity processAreaFile(File file, String horizon) throws IOException ;

     TrajectoryEntity saveTrajectory(TrajectoryEntity trajectory, List<AreaConfigEntity> areaConfigEntities);

    }
