package com.rte_france.antares.datamanager_back.exception;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

import java.util.List;

@Getter
@Setter
public class BusinessException extends AntaresException {

    @Builder
    public BusinessException(AntaresErrorCode antaresErrorCode, String message, List<String> errorMessageArguments, HttpStatus httpStatus) {
        super(antaresErrorCode, message, errorMessageArguments, httpStatus);
    }
}
