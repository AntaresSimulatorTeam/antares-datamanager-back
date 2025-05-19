package com.rte_france.antares.datamanager_back.exception;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

import java.text.MessageFormat;
import java.util.List;

@Getter
@Setter
public class BusinessException extends AntaresException {
    private static final int MAX_MESSAGE_LENGTH = 255;
    private static final String TRUNCATION_SUFFIX = "...";

    @Builder(builderClassName = "BusinessExceptionBuilder")
    public BusinessException(AntaresErrorCode antaresErrorCode, String message, List<String> errorMessageArguments, HttpStatus httpStatus) {
        super(antaresErrorCode,
                message,
                truncateMessageArguments(errorMessageArguments),
                httpStatus);
    }

    private static List<String> truncateMessageArguments(List<String> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return arguments;
        }

        return arguments.stream()
                .map(arg -> {
                    if (arg != null && arg.length() > MAX_MESSAGE_LENGTH) {
                        return arg.substring(0, MAX_MESSAGE_LENGTH - TRUNCATION_SUFFIX.length()) + TRUNCATION_SUFFIX;
                    }
                    return arg;
                })
                .toList();
    }
}
