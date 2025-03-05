package com.rte_france.antares.datamanager_back.service;

import com.fasterxml.jackson.core.JsonProcessingException;


public interface StudyGeneratorService {
    void buildJsonForStudyGeneration(Integer study_id) throws JsonProcessingException;

    void callGenerateStudyService(Integer studyId);

}
