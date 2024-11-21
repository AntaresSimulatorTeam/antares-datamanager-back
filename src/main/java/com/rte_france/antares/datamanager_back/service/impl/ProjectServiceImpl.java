package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.exception.ResourceNotFoundException;
import com.rte_france.antares.datamanager_back.repository.PinnedProjectRepository;
import com.rte_france.antares.datamanager_back.repository.ProjectRepository;
import com.rte_france.antares.datamanager_back.repository.model.PinnedProjectEntity;
import com.rte_france.antares.datamanager_back.repository.model.PinnedProjectEntityId;
import com.rte_france.antares.datamanager_back.repository.model.ProjectEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.service.ProjectService;
import com.rte_france.antares.datamanager_back.util.Utils;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final PinnedProjectRepository pinnedProjectRepository;

    private final ProjectRepository projectRepository;

    public List<ProjectEntity> getPinnedProjectsByUser(String userId) {
        return pinnedProjectRepository.findById_Nni(userId).stream()
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

            spec = spec.and(new PegaseSpecification(searchCriteriaWithProjectName))
                    .or(new PegaseSpecification(searchCriteriaWithTag))
                    .or(new PegaseSpecification(searchCriteriaWithUser))
                    .or(hasStudyName(search))
                    .or(creationDateSpec);
            return projectRepository.findAll(spec, paging);
        }
        return projectRepository.findAll(paging);
    }

    public static Specification<ProjectEntity> hasStudyName(String studyName) {
        return (Root<ProjectEntity> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) -> {
            Join<ProjectEntity, StudyEntity> studies = root.join("studies");
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

}
