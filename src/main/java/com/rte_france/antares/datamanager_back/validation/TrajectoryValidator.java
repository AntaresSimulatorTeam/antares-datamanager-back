package com.rte_france.antares.datamanager_back.validation;

import com.rte_france.antares.datamanager_back.exception.AntaresErrorCode;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class TrajectoryValidator {

    private static final int DEFAULT_MAX_LENGTH = 40;

    public void validate(String trajectoryName) {
        validate(trajectoryName, DEFAULT_MAX_LENGTH);
    }

    public void validate(String trajectoryName, int maxLength) {
        if (trajectoryName == null) {
            return;
        }

        if (trajectoryName.length() > maxLength) {
            throw BusinessException.builder()
                    .antaresErrorCode(AntaresErrorCode.INVALID_TRAJECTORY_NAME)
                    .message("Trajectory name cannot exceed " + maxLength + " characters")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        if (trajectoryName.trim().isEmpty()) {
            throw BusinessException.builder()
                    .antaresErrorCode(AntaresErrorCode.INVALID_TRAJECTORY_NAME)
                    .message("The name of the trajectory cannot contain only spaces")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }
}
