package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.dto.StudyDTO;
import com.rte_france.antares.datamanager_back.exception.BadRequestException;
import com.rte_france.antares.datamanager_back.repository.ProjectRepository;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.model.ProjectEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.service.StudyService;
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
import java.util.Optional;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudyServiceImpl implements StudyService {

    private final StudyRepository studyRepository;

    private final ProjectRepository projectRepository;

    @Override
    public Page<StudyEntity> findStudiesByCriteria(String search, Integer idProject, Pageable pageable) {
        Specification<StudyEntity> spec = Specification.where(null);

        // Ajouter le critère pour idProject s'il est non nul
        if (idProject != null) {
            spec = spec.and(hasProjectId(idProject));
        }

        // Ajouter les critères liés au "search" s'il est non nul et non vide
        if (search != null && !StringUtils.isEmpty(search)) {
            // Construire les différentes spécifications liées à la recherche
            Specification<StudyEntity> creationDateSpec = creationDateSpecification(search);

            Specification<StudyEntity> searchSpecs =
                    Specification.where(new PegaseSpecification(new SearchCriteria("name", ":", search)))
                            .or(new PegaseSpecification(new SearchCriteria("tags", "in", search)))
                            .or(new PegaseSpecification(new SearchCriteria("createdBy", ":", search)))
                            .or(new PegaseSpecification(new SearchCriteria("status", ":", search)))
                            .or(new PegaseSpecification(new SearchCriteria("horizon", ":", search)))
                            .or(hasProjectName(search))
                            .or(creationDateSpec);

            // Combiner les spécifications existantes avec celles liées à "search"
            spec = spec.and(searchSpecs);
        }

        // Retourner les résultats filtrés
        return studyRepository.findAll(spec, pageable);
    }

    @Override
    public List<String> searchKeywordsByPartialName(String partialName) {
        return studyRepository.findKeywordsByPartialName(partialName);
    }

        @Override
        public StudyDTO createStudy(StudyDTO studyDTO) {
            if (studyDTO.getProject() == null || studyDTO.getProject().isEmpty()) {
                throw new BadRequestException("Project name must be provided.");
            }

            Optional<ProjectEntity> projectEntityOptional = projectRepository.findByName(studyDTO.getProject());
            ProjectEntity projectEntity;

            if (projectEntityOptional.isPresent()) {
                projectEntity = projectEntityOptional.get();
            } else {
                projectEntity = new ProjectEntity();
                projectEntity.setName(studyDTO.getProject());
                projectEntity = projectRepository.save(projectEntity);
            }

            StudyEntity studyEntity = new StudyEntity();
            studyEntity.setName(studyDTO.getName());
            studyEntity.setCreatedBy(studyDTO.getCreatedBy());
            studyEntity.setCreationDate(LocalDateTime.now());
            studyEntity.setProject(projectEntity);
            studyEntity = studyRepository.save(studyEntity);

            studyDTO.setId(studyEntity.getId());
            studyDTO.setCreationDate(studyEntity.getCreationDate());

            return studyDTO;
        }

    public static Specification<StudyEntity> hasProjectName(String projectName) {
        return (Root<StudyEntity> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) -> {
            Join<StudyEntity, ProjectEntity> project = root.join("project");
            return criteriaBuilder.like(project.get("name"), "%" + projectName + "%");
        };
    }

    public static Specification<StudyEntity> hasProjectId(Integer projectId) {
        return (Root<StudyEntity> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) -> {
            Join<StudyEntity, ProjectEntity> project = root.join("project");
            return criteriaBuilder.equal(project.get("id"), projectId);
        };
    }

    /**
     * @param search if it is a LocalDateTime in format "yyyy-MM-dd'T'HH:mm:ss" the return is a Specification
     * @return Specification to look by StudyEntity creation date
     */
    private static Specification<StudyEntity> creationDateSpecification(String search) {
        LocalDateTime creationDate;
        if (Utils.hasValidDateFormat(search)) {
            creationDate = Utils.parseToLocalDateTime(search);
        } else {
            creationDate = null;
        }

        Specification<StudyEntity> creationDateSpec = null;
        if (creationDate != null) {
            creationDateSpec = (root, query, cb) -> cb.equal(root.get("creationDate"), creationDate);
        }
        return creationDateSpec;
    }

}
