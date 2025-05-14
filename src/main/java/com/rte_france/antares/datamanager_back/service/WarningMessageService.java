package com.rte_france.antares.datamanager_back.service;


public interface WarningMessageService {

    String getMessage(String code, Object... args);

    String getNotFoundMessage();

    void acknowledgeWarning(Integer id);

    }
