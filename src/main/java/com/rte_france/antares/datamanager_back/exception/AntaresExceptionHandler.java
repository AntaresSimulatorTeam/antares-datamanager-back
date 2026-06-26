package com.rte_france.antares.datamanager_back.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.text.MessageFormat;
import java.time.LocalDateTime;

@AutoConfiguration
@RestControllerAdvice
@Slf4j
@Order(1)
public class AntaresExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<AntaresExceptionDto> businessExceptionHandler(BusinessException ex) {
        BusinessException processedException = formatExceptionMessage(ex);
        logException("BusinessException", processedException.getAntaresErrorCode(), processedException.getMessage(), null);

        return ResponseEntity
                .status(processedException.getHttpStatus())
                .body(new AntaresExceptionDto(
                        processedException.getMessage(),
                        processedException.getAntaresErrorCode(),
                        processedException.getErrorMessageArguments(),
                        processedException.getDate(),
                        AntaresErrorType.BUSINESS.name()
                ));
    }

    @ExceptionHandler(TechnicalException.class)
    public ResponseEntity<AntaresExceptionDto> technicalExceptionHandler(TechnicalException ex) {
        TechnicalException processedException = formatTechnicalExceptionMessage(ex);
        logException("TechnicalException", processedException.getAntaresErrorCode(),
                processedException.getMessage(), processedException.getCause());

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new AntaresExceptionDto(
                        processedException.getMessage(),
                        processedException.getAntaresErrorCode(),
                        processedException.getErrorMessageArguments(),
                        processedException.getDate(),
                        AntaresErrorType.TECHNICAL.name()
                ));
    }


    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<AntaresExceptionDto> handleConstraintViolation(ConstraintViolationException ex) {

        String message = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse("Validation failed");

        BusinessException businessException = BusinessException.builder()
                .message(message)
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build();

        return businessExceptionHandler(businessException);
    }
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<AntaresExceptionDto> runtimeExceptionHandler(RuntimeException ex) {
        logException("RuntimeException", null, ex.getMessage(), ex.getCause());

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AntaresExceptionDto.builder()
                        .antaresErrorMessage("Une erreur technique s'est produite !")
                        .antaresErrorCode(AntaresErrorCode.SERVER_ERROR)
                        .date(LocalDateTime.now())
                        .type(AntaresErrorType.TECHNICAL.name())
                        .build());
    }

    private BusinessException formatExceptionMessage(BusinessException ex) {
        if (CollectionUtils.isEmpty(ex.getErrorMessageArguments())) {
            return ex;
        }
        String formattedMessage = MessageFormat.format(ex.getMessage(), ex.getErrorMessageArguments().toArray());
        return new BusinessException(ex.getAntaresErrorCode(), formattedMessage,
                ex.getErrorMessageArguments(), ex.getHttpStatus());
    }

    private TechnicalException formatTechnicalExceptionMessage(TechnicalException ex) {
        if (CollectionUtils.isEmpty(ex.getErrorMessageArguments()) || StringUtils.isEmpty(ex.getMessage())) {
            return ex;
        }
        String formattedMessage = MessageFormat.format(ex.getMessage(), ex.getErrorMessageArguments().toArray());
        return new TechnicalException(ex.getAntaresErrorCode(), formattedMessage,
                ex.getErrorMessageArguments(), ex.getCause());
    }

    private void logException(String exceptionType, Object errorCode, String message, Throwable cause) {
        if (errorCode != null) {
            log.error("{}: code={}, message={}", exceptionType, errorCode, message);
        } else {
            log.error("{}: {}", exceptionType, message);
        }
        if (cause != null) {
            log.error("Root cause: {}", cause.toString(), cause);
        }
    }
}