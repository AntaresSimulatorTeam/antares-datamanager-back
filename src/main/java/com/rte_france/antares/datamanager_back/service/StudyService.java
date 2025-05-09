package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.StudyDTO;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface StudyService {


    Page<StudyEntity> findStudiesByCriteria(String search,Integer idProject, Pageable pageable);

    StudyDTO createStudy(StudyDTO studyDTO);

    List<String> searchKeywordsByPartialName(String partialName);

    void deleteStudyById(Integer id);

    void updateStudyStatusAsGenerated(Integer studyId);

    StudyDTO findStudyById(Integer id);

    void generateStudy(Integer studyId);

}
