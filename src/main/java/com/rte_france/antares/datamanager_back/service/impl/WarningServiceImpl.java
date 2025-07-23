package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.WarningDTO;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.mapper.WarningMapper;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.StudyTrajectoryRepository;
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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarningServiceImpl implements WarningService {

    private final MessageSource messageSource;
    private final WarningRepository warningRepository;
    private final StudyRepository studyRepository;
    private final StudyTrajectoryRepository studyTrajectoryRepository;

    @Override
    public String getMessage(String code, Object... args) {
        String template = messageSource.getMessage(code, null, code, Locale.getDefault());
        if (template == null) throw new IllegalStateException("template is null");
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
     * @param studyId the ID of the study to which the trajectory belongs
     * @param trajectoryType the type of the trajectory for which warnings are to be retrieved
     * @return a set of WarningDTO objects representing the warnings for the specified trajectory,
     * or an empty list if no warnings are available
     */
    @Override
    public Set<WarningDTO> getWarningsForTrajectory(Integer studyId, TrajectoryType trajectoryType) {
        return warningRepository.findByTrajectoryTypeAndStudyId(studyId, trajectoryType.name())
                .stream()
                .filter(this::isWarningDependOnExistingLinkTrajectoryAndStudy
                )
                .map(WarningMapper::toWarningMessageDTO)
                .collect(Collectors.toSet());
    }

    public boolean isWarningDependOnExistingLinkTrajectoryAndStudy(WarningMessageEntity warningMessageEntity) {
        return studyTrajectoryRepository.existsById(
                StudyTrajectoryKey.builder()
                        .trajectoryId(warningMessageEntity.getTrajectory().getId())
                        .scenarioId(warningMessageEntity.getStudy().getId())
                        .build());
    }


}
