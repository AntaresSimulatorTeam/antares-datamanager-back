package com.rte_france.antares.datamanager_back.service.area_link;

import com.rte_france.antares.datamanager_back.repository.model.AreaConfigEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.nio.file.Path;
import java.util.List;

public interface AreaFileProcessorService {

     TrajectoryEntity processAreaFile(Path path, String horizon) throws Exception;

     TrajectoryEntity saveTrajectory(TrajectoryEntity trajectory, List<AreaConfigEntity> areaConfigEntities);

    }
