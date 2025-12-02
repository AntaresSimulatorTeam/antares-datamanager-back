package com.rte_france.antares.datamanager_back.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.text.MessageFormat;

@AutoConfiguration
@RestControllerAdvice
@Slf4j
@Order(1)
public class AntaresExceptionHandler extends ResponseEntityExceptionHandler {


    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<AntaresExceptionDto> businessExceptionHandler(BusinessException ex) {
        BusinessException businessException = ex;
        if (!CollectionUtils.isEmpty(ex.getErrorMessageArguments())) {
            String formatMessageWithArguments = MessageFormat.format(ex.getMessage(), ex.getErrorMessageArguments().toArray());
            businessException = new BusinessException(ex.getAntaresErrorCode(), formatMessageWithArguments, ex.getErrorMessageArguments(), ex.getHttpStatus());
        }

        log.error("BusinessException: code={}, message={}, args={}",
                businessException.getAntaresErrorCode(),
                businessException.getMessage(),
                businessException.getErrorMessageArguments());

        return ResponseEntity
                .status(businessException.getHttpStatus())
                .body(new AntaresExceptionDto(
                        businessException.getMessage(),
                        businessException.getAntaresErrorCode(),
                        businessException.getErrorMessageArguments(),
                        businessException.getDate(),
                        AntaresErrorType.BUSINESS.name()
                ));
    }


    @ExceptionHandler(TechnicalException.class)
    public ResponseEntity<AntaresExceptionDto> technicalExceptionHandler(TechnicalException ex) {
        TechnicalException technicalException = ex;
        if (!CollectionUtils.isEmpty(ex.getErrorMessageArguments()) && !StringUtils.isEmpty(ex.getMessage())) {
            String formatMessageWithArguments = MessageFormat.format(ex.getMessage(), ex.getErrorMessageArguments());
            technicalException = new TechnicalException(ex.getAntaresErrorCode(), formatMessageWithArguments, ex.getErrorMessageArguments(), ex.getCause());
        }
        log.error("TechnicalException attrapée : {}", ex.getMessage(), ex);
        if (ex.getCause() != null) {
            log.error("Cause profonde : {}", ex.getCause().toString(), ex.getCause());
        }
        log.debug(technicalException.toString(), technicalException);
        AntaresExceptionDto antaresExceptionDto = new AntaresExceptionDto(technicalException.getMessage(), technicalException.getAntaresErrorCode(), technicalException.getErrorMessageArguments(), technicalException.getDate(), AntaresErrorType.TECHNICAL.name());
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(antaresExceptionDto);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<AntaresExceptionDto> runtimeExceptionHandler(RuntimeException ex) {
        log.error(ex.toString(), ex);
        if (ex.getCause() != null) {
            log.error("Cause profonde : {}", ex.getCause().toString(), ex.getCause());
        }        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new AntaresExceptionDto(ex));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<AntaresExceptionDto> handleConstraintViolation(ConstraintViolationException ex) {

        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .findFirst()
                .orElse("Validation failed");

        BusinessException businessException = BusinessException.builder()
                .message(message)
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build();

        return businessExceptionHandler(businessException);
    }




}
