package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.exception.TechnicalException;


public interface StudyGeneratorService {
    void buildJsonForStudyGeneration(Integer studyId) throws TechnicalException;

    void callGenerateStudyService(Integer studyId);

}
