package com.rte_france.antares.datamanager_back.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
public class TechnicalAntaresDataMangerException extends RuntimeException {
    public TechnicalAntaresDataMangerException(String message) {
        super(message);
    }
}
