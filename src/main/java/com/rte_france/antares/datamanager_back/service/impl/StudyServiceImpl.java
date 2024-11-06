package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.model.ProjectEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.service.StudyService;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
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
                SearchCriteria searchCriteriaWithStatus = new SearchCriteria("status", ":", search);
                SearchCriteria searchCriteriaWithCreationDate = new SearchCriteria("creationDate", ":", search);
                SearchCriteria searchCriteriaWithHorizon = new SearchCriteria("horizon", ":", search);
                spec = spec.and(new StudySpecification(searchCriteriaWithFileName))
                        .or(new StudySpecification(searchCriteriaWithTag))
                        .or(new StudySpecification(searchCriteriaWithUser))
                        .or(new StudySpecification(searchCriteriaWithStatus))
                        .or(new StudySpecification(searchCriteriaWithCreationDate))
                        .or(new StudySpecification(searchCriteriaWithHorizon))
                        .or(hasProjectName(search));
                return studyRepository.findAll(spec, pageable);
            }
            return studyRepository.findAll(pageable);
        }

    public static Specification<StudyEntity> hasProjectName(String projectName) {
        return (Root<StudyEntity> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) -> {
            Join<StudyEntity, ProjectEntity> project = root.join("project");
            return criteriaBuilder.equal(project.get("name"), projectName);
        };
    }
}
