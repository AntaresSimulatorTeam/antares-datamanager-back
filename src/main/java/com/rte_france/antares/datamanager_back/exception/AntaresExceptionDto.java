package com.rte_france.antares.datamanager_back.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

import static java.time.LocalDateTime.now;


@Getter
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AntaresExceptionDto {

    private final String antaresErrorMessage;

    private final AntaresErrorCode antaresErrorCode;

    private final List<String> errorMessageArguments;

    private final LocalDateTime date;
    
    private final String type;


    public AntaresExceptionDto(String antaresErrorMessage , AntaresErrorCode antaresErrorCode, List<String> errorMessageArguments, LocalDateTime date, String type) {
        this.antaresErrorMessage = antaresErrorMessage;
        this.antaresErrorCode = antaresErrorCode;
        this.errorMessageArguments = errorMessageArguments;
        this.date = date;
        this.type = type;
    }

    public AntaresExceptionDto(RuntimeException exception) {
        this.antaresErrorMessage = exception.getMessage();
        this.antaresErrorCode = AntaresErrorCode.SERVER_ERROR;
        this.errorMessageArguments = null;
        this.type = AntaresErrorType.TECHNICAL.name();
        this.date = now();
    }
}
