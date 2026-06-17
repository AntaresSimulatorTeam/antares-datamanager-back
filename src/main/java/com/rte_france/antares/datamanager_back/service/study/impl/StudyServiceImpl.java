package com.rte_france.antares.datamanager_back.service.study.impl;

import com.rte_france.antares.datamanager_back.dto.StudyDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.mapper.StudyMapper;
import com.rte_france.antares.datamanager_back.repository.ProjectRepository;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.WarningRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.load.impl.LoadFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.service.study.StudyGeneratorService;
import com.rte_france.antares.datamanager_back.service.study.StudyService;
import com.rte_france.antares.datamanager_back.service.common.WarningService;
import com.rte_france.antares.datamanager_back.util.DuplicationTrajectoryUtils;
import com.rte_france.antares.datamanager_back.util.Utils;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.rte_france.antares.datamanager_back.mapper.StudyMapper.toStudyDTO;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudyServiceImpl implements StudyService {

    private final StudyRepository studyRepository;
    private final ProjectRepository projectRepository;
    private final TrajectoryRepository trajectoryRepository;
    private final WarningRepository warningRepository;
    private final WarningService warningService;
    private final StudyGeneratorService studyGeneratorService;
    private final TrajectoryServiceImpl trajectoryServiceImpl;
    private static final  int HORIZON_LOWER_BOUND = 2000;
    private static final  int HORIZON_UPPER_BOUND = 9999;
    private final UserService userService;
    private final TrajectoryServiceImpl trajectoryService;

    private static final String STUDY_NOT_FOUND = "Study with id {0} not found.";
    private static final String HORIZON_FORMAT = "%d-%s";

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
                            .message(STUDY_NOT_FOUND)
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
                        .message(STUDY_NOT_FOUND)
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
    @Transactional
    public StudyDTO duplicateStudy(StudyDTO studyDTO) throws IOException {
        validateHorizon(studyDTO);
        
        StudyEntity studyToDuplicate = loadStudyToDuplicateOrThrow(studyDTO);


        List<TrajectoryEntity> trajectoriesAvailable = getAvailableTrajectoriesForDuplication(
                studyToDuplicate,
                studyDTO.getHorizon()
        );

        TrajectoryEntity areaTrajectory = DuplicationTrajectoryUtils.validateAreaTrajectoryForDuplication(
                trajectoriesAvailable,
                studyToDuplicate.getTrajectories(),
                studyDTO.getHorizon()
        );

        studyDTO.setTrajectoryIds(List.of(areaTrajectory.getId()));
        StudyDTO savedStudyDTO = createStudy(studyDTO);

        DuplicationTrajectoryUtils.TrajectoryProcessingResult result = DuplicationTrajectoryUtils.processAndLinkTrajectories(areaTrajectory,
                trajectoriesAvailable,
                studyToDuplicate.getTrajectories()
        );

        List<String> missingTrajectoryKeys = result.missingTrajectoryKeys();

        handleDuplicationWarnings(result, missingTrajectoryKeys, savedStudyDTO, studyDTO);

        // lancer le control de coherence par type
        checkTrajectoryCoherenceByType(trajectoriesAvailable, savedStudyDTO.getId(), studyDTO.getCreatedBy());

        return savedStudyDTO;
    }

    private StudyEntity loadStudyToDuplicateOrThrow(StudyDTO studyDTO) {
        return studyRepository.findById(studyDTO.getId()).orElseThrow(() -> 
                BusinessException.builder()
                        .message("Study {0} not found")
                        .errorMessageArguments(List.of(studyDTO.getName()))
                        .httpStatus(HttpStatus.NOT_FOUND)
                        .build()
        );
    }

    private List<TrajectoryEntity> getAvailableTrajectoriesForDuplication(StudyEntity studyToDuplicate, String newHorizonYear) {
        Set<TrajectoryEntity> existingStudyTrajectories = studyToDuplicate.getTrajectories();
        String originalHorizonYear = extractHorizonYear(studyToDuplicate.getHorizon());

        if (Objects.equals(originalHorizonYear, newHorizonYear)) {
            return getTrajectoriesForSameHorizon(existingStudyTrajectories);
        } else {
            return getTrajectoriesForChangedHorizon(existingStudyTrajectories, newHorizonYear);
        }
    }

    private String extractHorizonYear(String horizon) {
        if (horizon != null && horizon.contains("-")) {
            return horizon.split("-")[1];
        }
        return horizon;
    }

    private List<TrajectoryEntity> getTrajectoriesForSameHorizon(Set<TrajectoryEntity> existingStudyTrajectories) {
        return new ArrayList<>(existingStudyTrajectories);
    }

    private List<TrajectoryEntity> getTrajectoriesForChangedHorizon(Set<TrajectoryEntity> existingStudyTrajectories, String newHorizonYear) {
        String newHorizonRange = String.format(HORIZON_FORMAT, Integer.parseInt(newHorizonYear) - 1, newHorizonYear);

        Set<DuplicationTrajectoryUtils.TrajectoryKey> requestedKeys = existingStudyTrajectories.stream()
                .filter(Objects::nonNull)
                .map(t -> new DuplicationTrajectoryUtils.TrajectoryKey(t.getFileName(), t.getType(), t.getArea()))
                .collect(Collectors.toSet());

        List<TrajectoryEntity> trajectories = new ArrayList<>();
        for (DuplicationTrajectoryUtils.TrajectoryKey key : requestedKeys) {
            trajectoryRepository.findLatestTrajectoriesByNamesAndHorizon(
                    newHorizonRange,
                    key.fileName(),
                    key.type(),
                    key.area(),
                    PageRequest.of(0, 1)
            ).stream().findFirst().ifPresent(trajectories::add);
        }
        return trajectories;
    }

    private void handleDuplicationWarnings(DuplicationTrajectoryUtils.TrajectoryProcessingResult result,
                                           List<String> missingTrajectoryKeys,
                                           StudyDTO savedStudyDTO,
                                           StudyDTO originalStudyDTO) {
        Set<String> missingWarnings = new LinkedHashSet<>(missingTrajectoryKeys);

        if (!missingWarnings.isEmpty()) {
            warningService.addWarning(
                    result.warningMessages(),
                    Arrays.asList(String.join(", ", missingWarnings), originalStudyDTO.getHorizon()),
                    WarningCode.DUPLICATION_MISSING_TRAJECTORIES,
                    savedStudyDTO.getId(),
                    originalStudyDTO.getCreatedBy(),
                    result.areaTrajectory()
            );
            warningRepository.saveAll(result.warningMessages());
        }
    }


    /**
     * Checks trajectory coherence for each trajectory type available.
     * This method groups trajectories by type and performs type-specific coherence checks
     * such as validation of areas, links, loads, thermal parameters, etc.
     *
     * @param trajectoriesAvailable list of available trajectories to check
     * @param studyId the ID of the study
     * @param userNni the NNI of the user performing the operation
     */
    private void checkTrajectoryCoherenceByType(List<TrajectoryEntity> trajectoriesAvailable, Integer studyId, String userNni) {
        if (CollectionUtils.isEmpty(trajectoriesAvailable)) {
            return;
        }

        // Group trajectories by type
        Map<String, List<TrajectoryEntity>> trajectoriesByType = trajectoriesAvailable.stream()
                .collect(Collectors.groupingBy(TrajectoryEntity::getType));

        // Check coherence for each type
        for (Map.Entry<String, List<TrajectoryEntity>> entry : trajectoriesByType.entrySet()) {
            String trajectoryType = entry.getKey();
            List<TrajectoryEntity> trajectories = entry.getValue();

            try {
                for (TrajectoryEntity trajectory : trajectories) {
                    Set<WarningMessageEntity> warningMessages = new HashSet<>();
                    trajectoryService.checkTrajectoryCoherence(studyId, warningMessages, trajectory, userNni);
                    trajectoryService.linkTrajectoryToStudy(trajectory.getId(), studyId, TrajectoryType.valueOf(trajectoryType));
                }
            } catch (IOException e) {
                log.error("Error checking trajectory coherence for type {} during duplication: {}", trajectoryType, e.getMessage(), e);
                throw TechnicalException.builder()
                        .message("Error checking trajectory coherence for type {0} during duplication")
                        .errorMessageArguments(List.of(trajectoryType))
                        .cause(e)
                        .build();
            }
        }
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

    private StudyEntity buildAndSaveNewStudy(StudyDTO studyDTO, ProjectEntity projectEntity) {
        String horizon = String.format(HORIZON_FORMAT, Integer.parseInt(studyDTO.getHorizon()) - 1, studyDTO.getHorizon());

        Set<TrajectoryEntity> trajectories = CollectionUtils.isEmpty(studyDTO.getTrajectoryIds())
                ? Collections.emptySet()
                : trajectoryRepository.findAllByIdWithWarnings(studyDTO.getTrajectoryIds());

        StudyEntity studyEntity = createBaseStudyEntity(studyDTO, projectEntity, horizon, trajectories);

        Set<WarningMessageEntity> clonedWarnings = cloneUniqueWarningsFromTrajectories(trajectories, studyDTO, studyEntity);
        studyEntity.getWarningMessages().addAll(clonedWarnings);

        return studyRepository.save(studyEntity);
    }

    private StudyEntity createBaseStudyEntity(StudyDTO studyDTO, ProjectEntity projectEntity, String horizon, Set<TrajectoryEntity> trajectories) {
        return StudyEntity.builder()
                .name(studyDTO.getName())
                .createdBy(studyDTO.getCreatedBy())
                .creationDate(LocalDateTime.now())
                .project(projectEntity)
                .horizon(horizon)
                .status(StudyStatus.IN_PROGRESS)
                .tags(studyDTO.getTags())
                .warningMessages(new HashSet<>())
                .trajectories(trajectories)
                .build();
    }

    private Set<WarningMessageEntity> cloneUniqueWarningsFromTrajectories(Set<TrajectoryEntity> trajectories, StudyDTO studyDTO, StudyEntity studyEntity) {
        Set<WarningMessageEntity> clonedWarnings = new HashSet<>();
        for (TrajectoryEntity trajectory : trajectories) {
            trajectory.getScenarioEntities().add(studyEntity);

            for (WarningMessageEntity originalWarning : trajectory.getWarningMessages()) {
                if (originalWarning.getStudy().getId().equals(studyDTO.getId()) && trajectoryServiceImpl.isStudyTrajectoryExistById(studyDTO.getId(), originalWarning)) {
                    clonedWarnings.add(cloneWarningMessage(originalWarning, studyEntity, trajectory));
                }
            }
        }

        return clonedWarnings;
    }

    private WarningMessageEntity cloneWarningMessage(WarningMessageEntity original, StudyEntity study, TrajectoryEntity trajectory) {
        return WarningMessageEntity.builder()
                .warningCode(original.getWarningCode())
                .warningLevel(original.getWarningLevel())
                .warningContent(original.getWarningContent())
                .createdBy(userService.getCurrentUserDetails().getNni())
                .creationDate(LocalDateTime.now())
                .trajectory(trajectory)
                .study(study)
                .isAck(original.getIsAck())
                .secondTrajectory(original.getSecondTrajectory())
                .build();
    }


    @Override
    public void updateStudyStatusAsGenerated(Integer studyId) {
        studyRepository.findById(studyId).ifPresentOrElse(
                studyEntity -> {
                    studyEntity.setStatus(StudyStatus.GENERATED);
                    studyEntity.setGenerationDate(LocalDateTime.now());
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

    private static void validateTags(StudyDTO studyDTO) {
        if (studyDTO.getTags() != null && studyDTO.getTags().size() > 6) {
            throw BusinessException.builder()
                    .message("Tags list must not exceed 6 items.")
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

    @Override
    @Transactional
    public StudyDTO updateStudy(Integer id, StudyDTO studyDTO) {
        var study = loadStudyIfExists(id);
        ensureNotGenerated(study);
        if (studyDTO.getProject() != null) updateProjectIfPresent(study, studyDTO);
        if (studyDTO.getName() != null) updateStudyNameIfPresent(study, studyDTO);
        if (studyDTO.getTags() != null) updateTagsIfPresent(study, studyDTO);
        var saved = studyRepository.save(study);
        return StudyMapper.toStudyDTO(saved);
    }

    private StudyEntity loadStudyIfExists(Integer id) {
        return studyRepository.findById(id).orElseThrow(() ->
                BusinessException.builder()
                        .message(STUDY_NOT_FOUND)
                        .errorMessageArguments(List.of(id.toString()))
                        .httpStatus(HttpStatus.NOT_FOUND)
                        .build()
        );
    }

    private void ensureNotGenerated(StudyEntity study) {
        if (StudyStatus.GENERATED.equals(study.getStatus())) {
            throw BusinessException.builder()
                    .message("Only studies with status IN_PROGRESS can be updated.")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private void updateProjectIfPresent(StudyEntity study, StudyDTO dto) {
        var newProject = projectRepository.findByName(dto.getProject())
                .orElseThrow(() -> BusinessException.builder()
                        .message("Project not found with name: {0}")
                        .errorMessageArguments(List.of(dto.getProject()))
                        .httpStatus(HttpStatus.NOT_FOUND)
                        .build());
        if (!Objects.equals(study.getProject().getId(), newProject.getId()) && studyExists(study.getName(), newProject.getName())) {
            throw BusinessException.builder()
                    .message("A study with the same name already exists for the target project.")
                    .httpStatus(HttpStatus.CONFLICT)
                    .build();
        }
        study.setProject(newProject);
    }

    private void updateTagsIfPresent(StudyEntity study, StudyDTO dto) {
        validateTags(dto);
        study.setTags(dto.getTags());
    }

    private void updateStudyNameIfPresent(StudyEntity study, StudyDTO studyDTO) {
        var studyNewName = studyDTO.getName() + "_" + (Integer.parseInt(studyDTO.getHorizon()));
        if (!Objects.equals(study.getName(), studyNewName) && studyExists(studyNewName, study.getProject().getName())) {
            throw BusinessException.builder()
                    .message("A study with the same name already exists for the target project.")
                    .httpStatus(HttpStatus.CONFLICT)
                    .build();
        }
        study.setName(studyNewName);
    }
}