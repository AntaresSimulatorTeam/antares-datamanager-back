package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.dto.WarningDTO;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.mapper.WarningMapper;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.WarningRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.WarningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarningServiceImpl implements WarningService {

    private final MessageSource messageSource;
    private final WarningRepository warningRepository;
    private final StudyRepository studyRepository;
    private final TrajectoryRepository trajectoryRepository;
    private WarningMapper warningMapper;


    @Override
    public String getMessage(String code, Object... args) {
        String template = messageSource.getMessage(code, null, code, Locale.getDefault());
        assert template != null;
        return MessageFormat.format(template, args); // Properly replaces {0}, {1}, etc.
    }


    @Override
    public String getNotFoundMessage() {
        return getMessage(WarningCode.DATA_NOT_FOUND.value());
    }

    public void acknowledgeWarning(Integer id) {
        WarningMessageEntity warning = warningRepository.findById(id)
                .orElseThrow(() -> BusinessException.builder().message("Warning message not found with id: " + id).httpStatus(HttpStatus.NOT_FOUND).build());
        warning.setIsAck(true);
        warningRepository.save(warning);
    }

    public void addWarning(Set<WarningMessageEntity> warningMessages,
                           List<String> warnings,
                           WarningCode warningCode,
                           Integer studyId,
                           String userNni,
                           TrajectoryEntity trajectory) {
        if (warnings.isEmpty()) {
            return;
        }

        StudyEntity study = studyRepository.findById(studyId).orElseThrow();

        Object[] messageArgs;
        if (warnings.size() == 1) {
            messageArgs = new Object[]{warnings.getFirst()};
        } else if (warnings.size() > 1) {
            messageArgs = new Object[]{String.join(", ", warnings)};
            if (warningCode == WarningCode.LOAD_MISSING_TRAJECTORY_FOR_AREAS ||
                    warningCode == WarningCode.DUPLICATION_MISSING_TRAJECTORIES
            ) {
                messageArgs = new Object[]{warnings.get(0), warnings.get(1)};
            }
        } else {
            return;
        }
        var messageContent = getMessage(warningCode.value(), messageArgs);
        boolean warningExists = warningRepository.existsByWarningContentAndTrajectoryIdAndStudyId(messageContent, trajectory.getId(), studyId);
        if (!warningExists) {
            var message = WarningMessageEntity.builder()
                    .warningContent(messageContent)
                    .warningLevel(WarningLevel.WARNING_LEVEL)
                    .secondTrajectory(null)
                    .warningCode(warningCode)
                    .study(study)
                    .trajectory(trajectory)
                    .creationDate(LocalDateTime.now())
                    .createdBy(userNni)
                    .isAck(false)
                    .build();

            warningMessages.add(message);
        }
    }

    /**
     * Retrieves a list of warnings associated with a specific trajectory.
     * The warnings are fetched from the repository, mapped to DTOs, and returned
     * as a list. If no warnings are available for the trajectory, an empty list is returned.
     *
     * @param trajectoryId the unique identifier of the trajectory for which warnings are to be retrieved
     * @return a list of WarningDTO objects representing the warnings for the specified trajectory,
     *         or an empty list if no warnings are available
     */
    @Override
    public Set<WarningDTO> getWarningsForTrajectory(Integer trajectoryId, Integer studyId) {
        return trajectoryRepository.findAllByIdWithWarnings(List.of(trajectoryId))
                .stream()
                .findFirst()
                .map(TrajectoryEntity::getWarningMessages)
                .map(warningMessages -> warningMessages.stream()
                        .filter(warning -> warning.getStudy().getId().equals(studyId))
                        .map(WarningMapper::toWarningMessageDTO)
                        .collect(Collectors.toSet()))
                .orElse(Collections.emptySet());
    }




}
