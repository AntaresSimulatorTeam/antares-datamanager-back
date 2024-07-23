package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.repository.model.LinkEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.io.File;
import java.io.IOException;
import java.util.List;

public interface LinkFileProcessorService {

     TrajectoryEntity processLinkFile(File file) throws IOException ;

     TrajectoryEntity saveTrajectory(TrajectoryEntity trajectory, List<LinkEntity> linkEntities);


    }
