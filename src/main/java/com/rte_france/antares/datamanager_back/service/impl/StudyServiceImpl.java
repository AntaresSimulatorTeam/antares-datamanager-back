package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.service.StudyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudyServiceImpl implements StudyService {

    private final StudyRepository studyRepository;


    @Override
    public Page<StudyEntity> findStudiesByCriteria(String search, Pageable pageable) {


            Specification<StudyEntity> spec = Specification.where(null);

            if (search != null) {
                SearchCriteria searchCriteriaWithFileName = new SearchCriteria("name", ":", search);
                SearchCriteria searchCriteriaWithTag = new SearchCriteria("tags", "in", search);
                SearchCriteria searchCriteriaWithUser = new SearchCriteria("createdBy", ":", search);
                spec = spec.and(new StudySpecification(searchCriteriaWithFileName))
                        .or(new StudySpecification(searchCriteriaWithTag))
                        .or(new StudySpecification(searchCriteriaWithUser));
                return studyRepository.findAll(spec, pageable);
            }
            return studyRepository.findAll(pageable);
        }

}
