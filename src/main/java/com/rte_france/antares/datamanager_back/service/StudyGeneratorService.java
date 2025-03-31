package com.rte_france.antares.datamanager_back.service;

import com.fasterxml.jackson.core.JsonProcessingException;


public interface StudyGeneratorService {
    void buildJsonForStudyGeneration(Integer studyId) throws JsonProcessingException;

    void callGenerateStudyService(Integer studyId);

}
