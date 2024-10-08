package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.ProjectDto;
import com.rte_france.antares.datamanager_back.dto.ProjectInputDto;
import com.rte_france.antares.datamanager_back.repository.model.ProjectEntity;

public interface ProjectService {

    ProjectEntity createProject(ProjectInputDto projectInputDto);
}
