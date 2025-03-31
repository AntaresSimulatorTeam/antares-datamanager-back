package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.repository.model.LinkEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.WarningMessageEntity;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public interface LinkFileProcessorService {

    TrajectoryEntity processLinkFile(Path path, String horizon, Integer studyId) throws IOException;

    TrajectoryEntity saveTrajectory(TrajectoryEntity trajectory, List<LinkEntity> linkEntities, Set<WarningMessageEntity> warningMessageEntities);

    void checkConsistencyTrajectoryLinkAndArea(List<LinkEntity> linkEntities, List<String> areaNames, Set<WarningMessageEntity> warningMessages, Integer studyId,Integer trajectoryId, TrajectoryEntity secondTrajectory);

    String validateLinkAreas(String link, List<String> areaNames);

    List<String> findListArea(Integer studyId);

    List<LinkEntity> findListLink(Integer studyId);

}
