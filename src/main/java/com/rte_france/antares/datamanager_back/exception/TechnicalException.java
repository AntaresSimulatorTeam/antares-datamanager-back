package com.rte_france.antares.datamanager_back.exception;

import lombok.Builder;

import java.util.List;

/**
 * Base exception for all technical exception
 */
public class TechnicalException extends AntaresException {

    @Builder
    public TechnicalException(AntaresErrorCode antaresErrorCode, String message, List<String> errorMessageArguments, Throwable cause) {
        super(antaresErrorCode, message, errorMessageArguments, cause);
    }
}
