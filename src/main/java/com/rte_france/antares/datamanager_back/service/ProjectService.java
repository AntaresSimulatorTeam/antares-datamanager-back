package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.repository.model.ProjectEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProjectService {

    List<ProjectEntity> getPinnedProjectsByUser(String userId);

    Page<ProjectEntity> findProjectsByCriteria(String search, Pageable paging);

    void deletePinnedProjectForGivenUser(String userId, Integer projectId);

    void pinProjectForUser(String userId, Integer projectId);


}

