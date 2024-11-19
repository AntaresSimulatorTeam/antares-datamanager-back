package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.exception.ResourceNotFoundException;
import com.rte_france.antares.datamanager_back.repository.PinnedProjectRepository;
import com.rte_france.antares.datamanager_back.repository.model.PinnedProjectEntity;
import com.rte_france.antares.datamanager_back.repository.model.PinnedProjectEntityId;
import com.rte_france.antares.datamanager_back.repository.model.ProjectEntity;
import com.rte_france.antares.datamanager_back.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final PinnedProjectRepository pinnedProjectRepository;

    public List<ProjectEntity> getPinnedProjectsByUser(String userId) {
        return pinnedProjectRepository.findById_Nni(userId).stream()
                .sorted((p1, p2) -> p2.getProject().getCreationDate().compareTo(p1.getProject().getCreationDate()))
                .limit(3)
                .map(PinnedProjectEntity::getProject)
                .toList();
    }

    @Override
    public void deletePinnedProjectForGivenUser(String userId, Integer projectId) {
        PinnedProjectEntityId pinnedProjectEntityId = new PinnedProjectEntityId(userId, projectId);

        boolean exists = pinnedProjectRepository.existsById(pinnedProjectEntityId);
        if (!exists) {
            throw new ResourceNotFoundException("Pinned project not found for user: " + userId + ", project ID: " + projectId);
        }

        pinnedProjectRepository.deletePinnedProjectEntityById(pinnedProjectEntityId);
    }

}
