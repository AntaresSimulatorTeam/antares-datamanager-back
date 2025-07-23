package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.dto.ProjectDto;
import com.rte_france.antares.datamanager_back.dto.ProjectInputDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
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
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static java.util.Objects.isNull;

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
        Specification<ProjectEntity> spec = (root, query, criteriaBuilder) -> {
            query.distinct(true);

            Predicate finalPredicate = criteriaBuilder.conjunction();

            if (StringUtils.isNotBlank(search)) {
                Predicate namePredicate = criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), "%" + search.toLowerCase() + "%");
                Predicate createdByPredicate = criteriaBuilder.like(criteriaBuilder.lower(root.get("createdBy")), "%" + search.toLowerCase() + "%");

                Join<ProjectEntity, String> tagsJoin = root.join("tags", JoinType.LEFT);
                Predicate tagPredicate = criteriaBuilder.like(criteriaBuilder.lower(tagsJoin), "%" + search.toLowerCase() + "%");

                Join<ProjectEntity, StudyEntity> studyJoin = root.join("studies", JoinType.LEFT);
                Predicate studyPredicate = criteriaBuilder.like(criteriaBuilder.lower(studyJoin.get("name")), "%" + search.toLowerCase() + "%");

                Predicate datePredicate = null;
                if (Utils.hasValidDateFormat(search)) {
                    LocalDateTime creationDate = Utils.parseToLocalDateTime(search);
                    datePredicate = criteriaBuilder.equal(root.get("creationDate"), creationDate);
                }

                finalPredicate = criteriaBuilder.or(
                        namePredicate,
                        createdByPredicate,
                        tagPredicate,
                        studyPredicate,
                        datePredicate != null ? datePredicate : criteriaBuilder.disjunction()
                );
            }

            return finalPredicate;
        };

        return projectRepository.findAll(spec, paging);
    }

    @Override
    public List<ProjectDto> searchProjectsByName(String partialName) {
        List<ProjectEntity> projectEntities = projectRepository.findByNameContainingIgnoreCase(partialName);
        return projectEntities.stream()
                .map(ProjectMapper::toProjectDto)
                .toList();
    }

    @Override
    public void deletePinnedProjectForGivenUser(String userId, Integer projectId) {
        PinnedProjectEntityId pinnedProjectEntityId = new PinnedProjectEntityId(userId, projectId);

        if (!pinnedProjectRepository.existsById(pinnedProjectEntityId)) {
            throw BusinessException.builder()
                    .message("Pinned project not found for user: {0}, project ID: {1}")
                    .errorMessageArguments(List.of(userId, projectId.toString()))
                    .httpStatus(HttpStatus.NOT_FOUND)
                    .build();
        }
        pinnedProjectRepository.deletePinnedProjectEntityById(pinnedProjectEntityId);
    }

    @Override
    public ProjectEntity findProjectById(Integer projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> BusinessException.builder()
                        .message("Project with ID: {0} not found")
                        .errorMessageArguments(List.of(projectId.toString()))
                        .httpStatus(HttpStatus.NOT_FOUND)
                        .build());
    }

    @Transactional
    public ProjectEntity pinProjectForUser(String userId, Integer projectId) {
        String nni = userService.getCurrentUserDetails().getNni();

        if (!userId.equals(nni)) {
            throw BusinessException.builder()
                    .message("User ID does not match the authenticated user's ID.")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        checkIfUserHasAlreadyMaxPinnedProjects(userId);

        PinnedProjectEntityId pinnedProjectEntityId = PinnedProjectEntityId.builder()
                .projectId(projectId)
                .nni(nni)
                .build();

        pinnedProjectRepository.findById(pinnedProjectEntityId).ifPresent(pinnedProject -> {
            throw BusinessException.builder()
                    .message("Project already pinned")
                    .httpStatus(HttpStatus.CONFLICT)
                    .build();
        });

        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> BusinessException.builder()
                        .message("Project not found with ID: {0}")
                        .errorMessageArguments(List.of(projectId.toString()))
                        .httpStatus(HttpStatus.NOT_FOUND)
                        .build());

        PinnedProjectEntity pinnedProjectEntity = new PinnedProjectEntity();
        pinnedProjectEntity.setId(pinnedProjectEntityId);
        pinnedProjectEntity.setProject(project);

        pinnedProjectRepository.save(pinnedProjectEntity);

        return project;
    }

    @Override
    public void deleteProjectById(Integer projectId) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> BusinessException.builder()
                        .message("Project not found with ID: {0}")
                        .errorMessageArguments(List.of(projectId.toString()))
                        .httpStatus(HttpStatus.NOT_FOUND)
                        .build());

        if (project.getStudies() != null && !project.getStudies().isEmpty()) {
            throw BusinessException.builder()
                    .message("Project contains studies and cannot be deleted")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        projectRepository.deleteById(projectId);
    }

    private void checkIfUserHasAlreadyMaxPinnedProjects(String userId) {
        List<PinnedProjectEntity> pinnedProjects = pinnedProjectRepository.findByIdNni(userId);
        if (pinnedProjects.size() >= 3) {
            throw BusinessException.builder()
                    .message("Maximum number of pinned projects reached.")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    @Override
    public ProjectEntity createProject(ProjectInputDto projectInputDto) {
        if (StringUtils.isBlank(projectInputDto.getName())) {
            throw BusinessException.builder()
                    .message("Project name is required.")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        if (projectRepository.findByName(projectInputDto.getName()).isPresent()) {
            throw BusinessException.builder()
                    .message("A project with the same name already exists.")
                    .httpStatus(HttpStatus.CONFLICT)
                    .build();
        }

        if (projectInputDto.getTags() != null && projectInputDto.getTags().size() > 6) {
            throw BusinessException.builder()
                    .message("A project cannot have more than 6 tags.")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        ProjectEntity newProject = new ProjectEntity();
        newProject.setName(projectInputDto.getName());
        newProject.setCreationDate(LocalDateTime.now());
        newProject.setCreatedBy(userService.getCurrentUserDetails().getNni());
        newProject.setDescription(projectInputDto.getDescription());
        newProject.setTags(projectInputDto.getTags());
        return projectRepository.save(newProject);
    }

    @Override
    public ProjectEntity updateProject(Integer projectId, ProjectInputDto projectInputDto) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> BusinessException.builder()
                        .message("Project not found with ID: {0}")
                        .errorMessageArguments(List.of(projectId.toString()))
                        .httpStatus(HttpStatus.NOT_FOUND)
                        .build());
        if (projectInputDto.getTags() != null && projectInputDto.getTags().size() > 6) {
            throw BusinessException.builder()
                    .message("A project cannot have more than 6 tags.")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        if (!isNull(projectInputDto.getDescription())) project.setDescription(projectInputDto.getDescription());
        if (!isNull(projectInputDto.getTags())) project.setTags(projectInputDto.getTags());
        return projectRepository.save(project);
    }
}