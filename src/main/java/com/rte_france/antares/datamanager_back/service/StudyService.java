package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface StudyService {


    Page<StudyEntity> findStudiesByCriteria(String search, Pageable pageable);

}
