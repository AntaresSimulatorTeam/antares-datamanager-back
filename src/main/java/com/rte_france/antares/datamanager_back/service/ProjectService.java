package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.repository.model.ProjectEntity;

import java.util.List;

public interface ProjectService {

    List<ProjectEntity> getPinnedProjectsByUser(String userId);

    void deletePinnedProjectForGivenUser(String userId, Integer projectId);

}

