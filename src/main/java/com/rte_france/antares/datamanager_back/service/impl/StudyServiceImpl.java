package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.dto.StudyDTO;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.mapper.StudyMapper;
import com.rte_france.antares.datamanager_back.repository.ProjectRepository;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.ProjectEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyStatus;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.StudyGeneratorService;
import com.rte_france.antares.datamanager_back.service.StudyService;
import com.rte_france.antares.datamanager_back.util.Utils;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final StudyGeneratorService studyGeneratorService;
    private final static int HORIZON_LOWER_BOUND = 2000;
    private final static int HORIZON_UPPER_BOUND = 9000;


    @Override
    public Page<StudyEntity> findStudiesByCriteria(String search, Integer idProject, Pageable pageable) {
        Specification<StudyEntity> spec = (root, query, cb) -> {
            query.distinct(true);
            Predicate predicate = cb.conjunction();

            if (idProject != null) {
                Join<StudyEntity, ProjectEntity> project = root.join("project");
                predicate = cb.and(predicate, cb.equal(project.get("id"), idProject));
            }

            if (StringUtils.isNotBlank(search)) {
                Predicate searchPredicate = cb.disjunction();
                searchPredicate = cb.or(searchPredicate, cb.like(cb.lower(root.get("name")), "%" + search.toLowerCase() + "%"));
                searchPredicate = cb.or(searchPredicate, cb.isMember(search.toLowerCase(), root.get("tags")));
                searchPredicate = cb.or(searchPredicate, cb.like(cb.lower(root.get("createdBy")), "%" + search.toLowerCase() + "%"));
                searchPredicate = cb.or(searchPredicate, cb.like(cb.lower(root.get("status")), "%" + search.toLowerCase() + "%"));
                searchPredicate = cb.or(searchPredicate, cb.like(cb.lower(root.get("horizon")), "%" + search.toLowerCase() + "%"));

                Join<StudyEntity, ProjectEntity> projectJoin = root.join("project");
                searchPredicate = cb.or(searchPredicate, cb.like(cb.lower(projectJoin.get("name")), "%" + search.toLowerCase() + "%"));

                if (Utils.hasValidDateFormat(search)) {
                    LocalDateTime date = Utils.parseToLocalDateTime(search);
                    searchPredicate = cb.or(searchPredicate, cb.equal(root.get("creationDate"), date));
                }

                predicate = cb.and(predicate, searchPredicate);
            }

            return predicate;
        };

        return studyRepository.findAll(spec, pageable);
    }

    @Override
    public List<String> searchKeywordsByPartialName(String partialName) {
        return studyRepository.findKeywordsByPartialName(partialName);
    }

    @Override
    public void deleteStudyById(Integer id) {
        studyRepository.findById(id).ifPresentOrElse(
                studyRepository::delete,
                () -> {
                    throw BusinessException.builder()
                            .message("Study with id {0} not found.")
                            .errorMessageArguments(List.of(id.toString()))
                            .httpStatus(HttpStatus.NOT_FOUND)
                            .build();
                }
        );
    }

    @Override
    public StudyDTO findStudyById(Integer id) {
        return studyRepository.findById(id)
                .map(StudyMapper::toStudyDTO)
                .orElseThrow(() -> BusinessException.builder()
                        .message("Study with id {0} not found.")
                        .errorMessageArguments(List.of(id.toString()))
                        .httpStatus(HttpStatus.NOT_FOUND)
                        .build());
    }

    @Transactional
    @Override
    public void generateStudy(Integer studyId) throws TechnicalException {
        studyGeneratorService.buildJsonForStudyGeneration(studyId);
        studyGeneratorService.callGenerateStudyService(studyId);
        updateStudyStatusAsGenerated(studyId);
    }

    @Override
    public StudyDTO createStudy(StudyDTO studyDTO) {
        Assert.notNull(studyDTO.getName(), "Study name must be provided.");
        Assert.notNull(studyDTO.getProject(), "Project name must be provided.");
        Assert.notNull(studyDTO.getHorizon(), "Horizon year must be provided.");

        String studyName = studyDTO.getName() + "_" + (Integer.parseInt(studyDTO.getHorizon()));
        studyDTO.setName(studyName);

        validateHorizon(studyDTO);
        validateTags(studyDTO);

        if (studyExists(studyDTO.getName(), studyDTO.getProject())) {
            throw BusinessException.builder()
                    .message("A study with the same name already exists for the given project.")
                    .httpStatus(HttpStatus.CONFLICT)
                    .build();
        }

        ProjectEntity projectEntity = projectRepository.findByName(studyDTO.getProject())
                .orElseThrow(() -> BusinessException.builder()
                        .message("Project not found with name: {0}")
                        .errorMessageArguments(List.of(studyDTO.getProject()))
                        .httpStatus(HttpStatus.NOT_FOUND)
                        .build());

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
                    throw BusinessException.builder()
                            .message("Study not found with ID: {0}")
                            .errorMessageArguments(List.of(studyId.toString()))
                            .httpStatus(HttpStatus.NOT_FOUND)
                            .build();
                }
        );
    }

    private StudyEntity buildAndSaveNewStudy(StudyDTO studyDTO, ProjectEntity projectEntity) {
        String horizon = (Integer.parseInt(studyDTO.getHorizon()) - 1) + "-" + studyDTO.getHorizon() ;
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
            throw BusinessException.builder()
                    .message("Tags list must not exceed 10 items.")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private boolean studyExists(String studyName, String projectName) {
        return studyRepository.existsByNameAndProjectName(studyName, projectName);
    }

    private static void validateHorizon(StudyDTO studyDTO) {
        try {
            int horizonYear = Integer.parseInt(studyDTO.getHorizon());
            if (horizonYear < HORIZON_LOWER_BOUND || horizonYear > HORIZON_UPPER_BOUND) {
                throw BusinessException.builder()
                        .message("Horizon must be between 2000 and 9999")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
        } catch (NumberFormatException e) {
            throw BusinessException.builder()
                    .message("Horizon must be a valid year.")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }
}