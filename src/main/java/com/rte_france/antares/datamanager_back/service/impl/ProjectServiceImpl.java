package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.dto.ProjectDto;
import com.rte_france.antares.datamanager_back.dto.ProjectInputDto;
import com.rte_france.antares.datamanager_back.exception.BadRequestException;
import com.rte_france.antares.datamanager_back.exception.ResourceNotFoundException;
import com.rte_france.antares.datamanager_back.mapper.ProjectMapper;
import com.rte_france.antares.datamanager_back.repository.PinnedProjectRepository;
import com.rte_france.antares.datamanager_back.repository.ProjectRepository;
import com.rte_france.antares.datamanager_back.repository.model.PinnedProjectEntity;
import com.rte_france.antares.datamanager_back.repository.model.PinnedProjectEntityId;
import com.rte_france.antares.datamanager_back.repository.model.ProjectEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.service.ProjectService;
import com.rte_france.antares.datamanager_back.util.Utils;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final PinnedProjectRepository pinnedProjectRepository;
    private final ProjectRepository projectRepository;
    private final UserService userService;

    public List<ProjectEntity> getPinnedProjectsByUser(String userId) {
        return pinnedProjectRepository.findByIdNni(userId).stream()
                .sorted((p1, p2) -> p2.getProject().getCreationDate().compareTo(p1.getProject().getCreationDate()))
                .limit(3)
                .map(PinnedProjectEntity::getProject)
                .toList();
    }

    @Override
    public Page<ProjectEntity> findProjectsByCriteria(String search, Pageable paging) {
        Specification<ProjectEntity> spec = Specification.where(null);

        if (search != null && !StringUtils.isEmpty(search)) {

            Specification<ProjectEntity> creationDateSpec = creationDateSpecification(search);

            SearchCriteria searchCriteriaWithProjectName = new SearchCriteria("name", ":", search);
            SearchCriteria searchCriteriaWithTag = new SearchCriteria("tags", "in", search);
            SearchCriteria searchCriteriaWithUser = new SearchCriteria("createdBy", ":", search);

            spec = spec.and(new PegaseSpecification<>(searchCriteriaWithProjectName))
                    .or(new PegaseSpecification<>(searchCriteriaWithTag))
                    .or(new PegaseSpecification<>(searchCriteriaWithUser))
                    .or(hasStudyName(search))
                    .or(creationDateSpec);
            return projectRepository.findAll(spec, paging);
        }
        return projectRepository.findAll(paging);
    }

    @Override
    public List<ProjectDto> searchProjectsByName(String partialName) {
        List<ProjectEntity> projectEntities = projectRepository.findByNameContainingIgnoreCase(partialName);
        return projectEntities.stream()
                .map(ProjectMapper::toProjectDto)
                .toList();
    }

public static Specification<ProjectEntity> hasStudyName(String studyName) {
    return (Root<ProjectEntity> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) -> {
        Join<ProjectEntity, StudyEntity> studies = root.join("studies", JoinType.LEFT);
        return criteriaBuilder.like(studies.get("name"), "%" + studyName + "%");
    };
}

    /**
     * @param search if it is a LocalDateTime in format "yyyy-MM-dd'T'HH:mm:ss" the return is a Specification
     * @return Specification to look by StudyEntity creation date
     */
    public static Specification<ProjectEntity> creationDateSpecification(String search) {
        LocalDateTime creationDate;
        if (Utils.hasValidDateFormat(search)) {
            creationDate = Utils.parseToLocalDateTime(search);
        } else {
            creationDate = null;
        }

        Specification<ProjectEntity> creationDateSpec = null;
        if (creationDate != null) {
            creationDateSpec = (root, query, cb) -> cb.equal(root.get("creationDate"), creationDate);

        }
        return creationDateSpec;
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

    @Override
    public ProjectEntity findProjectById(Integer projectId) {
        Optional<ProjectEntity> projectDetails = projectRepository.findById(projectId);
        if (projectDetails.isPresent()) {
            return projectDetails.get();
        } else
            throw new ResourceNotFoundException("Project with ID: " + projectId + " not found");
    }

    @Transactional
    public ProjectEntity pinProjectForUser(String userId, Integer projectId) {
        String nni = userService.getCurrentUserDetails().getNni();

        // Check if the userId corresponds to the authenticated user's nni
        if (!userId.equals(nni)) {
            throw new BadRequestException("User ID does not match the authenticated user's ID.");
        }
        checkIfUserHasALreadyMaxPinnedProjects(userId);
        // Build the composite key for the PinnedProjectEntity
        PinnedProjectEntityId pinnedProjectEntityId = PinnedProjectEntityId.builder()
                .projectId(projectId)
                .nni(nni)
                .build();

        // Check if the project is already pinned for the user
        pinnedProjectRepository.findById(pinnedProjectEntityId).ifPresent(pinnedProject -> {
            throw new BadRequestException(
                    "Project already pinned"
            );
        });

        // Fetch the project entity or throw an exception if not found
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with ID: " + projectId));

        // Create and save the pinned project entity
        PinnedProjectEntity pinnedProjectEntity = new PinnedProjectEntity();
        pinnedProjectEntity.setId(pinnedProjectEntityId);
        pinnedProjectEntity.setProject(project);

        pinnedProjectRepository.save(pinnedProjectEntity);

        // Return the project associated with the pinned entity
        return project;
    }

    @Override
    public void deleteProjectById(Integer projectId) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with ID: " + projectId));
        if (project.getStudies() != null && !project.getStudies().isEmpty()) {
            throw new BadRequestException("Project contains studies and cannot be deleted");
        }
        projectRepository.deleteById(projectId);
    }

    private void checkIfUserHasALreadyMaxPinnedProjects(String userId) {
        List<PinnedProjectEntity> pinnedProjects = pinnedProjectRepository.findByIdNni(userId);
        if (pinnedProjects.size() >= 3) {
            throw new BadRequestException("Maximum number of pinned projects reached.");
        }
    }

    @Override
    public ProjectEntity createProject(ProjectInputDto projectInputDto) {
        if (StringUtils.isBlank(projectInputDto.getName())) {
            throw new IllegalArgumentException("Project name is required.");
        }

        Optional<ProjectEntity> existingProject = projectRepository.findByName(projectInputDto.getName());

        if (existingProject.isPresent()) {
            throw new IllegalArgumentException("A project with the same name already exists.");
        }

        if (projectInputDto.getTags() != null && projectInputDto.getTags().size() > 6) {
            throw new IllegalArgumentException("A project cannot have more than 6 tags.");
        }

        ProjectEntity newProject = new ProjectEntity();
        newProject.setName(projectInputDto.getName());
        newProject.setCreationDate(LocalDateTime.now());
        newProject.setCreatedBy(userService.getCurrentUserDetails().getNni());
        newProject.setDescription(projectInputDto.getDescription());
        newProject.setTags(projectInputDto.getTags());
        return projectRepository.save(newProject);
    }

}
