package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.dto.StudyDTO;
import com.rte_france.antares.datamanager_back.exception.BadRequestException;
import com.rte_france.antares.datamanager_back.repository.ProjectRepository;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.ProjectEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyStatus;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
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
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.rte_france.antares.datamanager_back.mapper.StudyMapper.toStudyDTO;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudyServiceImpl implements StudyService {

    private final StudyRepository studyRepository;

    private final ProjectRepository projectRepository;

    private final TrajectoryRepository trajectoryRepository;

    private final UserService userService;

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
    public void deleteStudyById(Integer id) {
        //delete study if exists
        studyRepository.findById(id).ifPresentOrElse(studyRepository::delete, () -> {
            throw new BadRequestException("Study with id " + id + " not found.");
        });
    }

    @Override
    public StudyDTO createStudy(StudyDTO studyDTO) {
        Assert.notNull(studyDTO.getName(), "Study name must be provided.");
        Assert.notNull(studyDTO.getProject(), "Project name must be provided.");
        Assert.notNull(studyDTO.getHorizon(), "Horizon year must be provided.");

        String studyName = studyDTO.getName() + "-" + (Integer.parseInt(studyDTO.getHorizon()) + 1) + "_REF";
        studyDTO.setName(studyName);
        if (studyDTO.getProject() == null || studyDTO.getProject().isEmpty()) {
            throw new BadRequestException("Project name must be provided.");
        }
        validateHorizon(studyDTO);
        validateTags(studyDTO);

        if (studyExists(studyDTO.getName(), studyDTO.getProject())) {
            throw new BadRequestException("A study with the same name already exists for the given project.");
        }

        ProjectEntity projectEntity = projectRepository.findByName(studyDTO.getProject())
                .orElseThrow(() -> new BadRequestException("Project not found with name: " + studyDTO.getProject()));

        return toStudyDTO(buildAndSaveNewStudy(studyDTO, projectEntity));
    }

    @Override
    public void updateStudyStatusAsGenerated(Integer studyId) {
        studyRepository.findById(studyId).ifPresentOrElse(
                studyEntity -> {
                    studyEntity.setStatus(StudyStatus.GENERATED);
                    studyRepository.save(studyEntity);
                },
                () -> {
                    throw new IllegalArgumentException("Study not found with ID: " + studyId);
                }
        );
    }

    private StudyEntity buildAndSaveNewStudy(StudyDTO studyDTO, ProjectEntity projectEntity) {
        String horizon = studyDTO.getHorizon() + "-" + (Integer.parseInt(studyDTO.getHorizon()) + 1);
        Set<TrajectoryEntity> trajectories = CollectionUtils.isEmpty(studyDTO.getTrajectoryIds())
                ? Collections.emptySet()
                : convertToTrajectoryEntities(studyDTO.getTrajectoryIds());

        StudyEntity studyEntity = StudyEntity.builder()
                .name(studyDTO.getName())
                .createdBy(studyDTO.getCreatedBy())
                .creationDate(LocalDateTime.now())
                .project(projectEntity)
                .horizon(horizon)
                .status(StudyStatus.IN_PROGRESS)
                .tags(studyDTO.getTags())
                .trajectories(trajectories)
                .build();

        trajectories.forEach(trajectory -> trajectory.getScenarioEntities().add(studyEntity));
        return studyRepository.save(studyEntity);
    }

    private Set<TrajectoryEntity> convertToTrajectoryEntities(List<Integer> trajectoryIds) {
        return trajectoryIds.stream()
                .map(trajectoryRepository::findTrajectoryEntityById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
    }

    private static void validateTags(StudyDTO studyDTO) {
        if (studyDTO.getTags() != null && studyDTO.getTags().size() > 10) {
            throw new BadRequestException("Tags list must not exceed 10 items.");
        }
    }

    private boolean studyExists(String studyName, String projectName) {
        return studyRepository.existsByNameAndProjectName(studyName, projectName);
    }

    private static void validateHorizon(StudyDTO studyDTO) {
        int currentYear = LocalDateTime.now().getYear();
        try {
            int horizonYear = Integer.parseInt(studyDTO.getHorizon());
            if (horizonYear < currentYear) {
                throw new BadRequestException("Horizon year must be greater than the current year.");
            }
        } catch (NumberFormatException e) {
            throw new BadRequestException("Horizon must be a valid year.");
        }
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
