package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.WarningMessageRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.WarningMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarningMessageServiceImpl implements WarningMessageService {

    private final MessageSource messageSource;
    private final WarningMessageRepository warningMessageRepository;
    private final StudyRepository studyRepository;

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
        WarningMessageEntity warning = warningMessageRepository.findById(id)
                .orElseThrow(() -> BusinessException.builder().message("Warning message not found with id: " + id).httpStatus(HttpStatus.NOT_FOUND).build());
        warning.setIsAck(true);
        warningMessageRepository.save(warning);
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
            if (warningCode == WarningCode.LOAD_MISSING_TRAJECTORY_FOR_AREAS) {
                messageArgs = new Object[]{warnings.get(0), warnings.get(1)};
            }
        } else {
            return;
        }
        var messageContent = getMessage(warningCode.value(), messageArgs);
        boolean warningExists = warningMessageRepository.existsByWarningContentAndTrajectoryIdAndStudyId(messageContent, trajectory.getId(), studyId);
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

}
