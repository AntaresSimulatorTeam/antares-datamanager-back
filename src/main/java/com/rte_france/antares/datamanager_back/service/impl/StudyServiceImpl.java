package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.model.ProjectEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.service.StudyService;
import com.rte_france.antares.datamanager_back.util.Utils;
import jakarta.persistence.criteria.Join;
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

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudyServiceImpl implements StudyService {

    private final StudyRepository studyRepository;


    @Override
    public Page<StudyEntity> findStudiesByCriteria(String search, Pageable pageable) {




            Specification<StudyEntity> spec = Specification.where(null);

            if (search != null) {

                Specification<StudyEntity> creationDateSpec = creationDateSpecification(search);

                SearchCriteria searchCriteriaWithFileName = new SearchCriteria("name", ":", search);
                SearchCriteria searchCriteriaWithTag = new SearchCriteria("tags", "in", search);
                SearchCriteria searchCriteriaWithUser = new SearchCriteria("createdBy", ":", search);

                Specification<StudyEntity> projectNameSpec = (root, query, cb) -> {
                    Join<StudyEntity, ProjectEntity> projectJoin = root.join("project");
                    return cb.equal(projectJoin.get("name"), search);
                };
                SearchCriteria searchCriteriaWithStatus = new SearchCriteria("status", ":", search);

                SearchCriteria searchCriteriaWithHorizon = new SearchCriteria("horizon", ":", search);
                spec = spec.and(new StudySpecification(searchCriteriaWithFileName))
                        .or(new StudySpecification(searchCriteriaWithTag))
                        .or(new StudySpecification(searchCriteriaWithUser))
                        .or(projectNameSpec)
                        .or(new StudySpecification(searchCriteriaWithStatus))
                        .or(new StudySpecification(searchCriteriaWithCreationDate))
                        .or(new StudySpecification(searchCriteriaWithHorizon))
                        .or(hasProjectName(search));
                        .or(creationDateSpec)
                        .or(new StudySpecification(searchCriteriaWithHorizon));
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
    /**
     * @param search if it is a LocalDateTime in format "yyyy-MM-dd'T'HH:mm:ss" the return is a Specification
     * @return Specification to look by StudyEntity creation date
     */
    private static Specification<StudyEntity> creationDateSpecification(String search) {
        LocalDateTime creationDate;
        if (Utils.hasValidDateFormat(search)) {
            creationDate = Utils.parseToLocalDateTime(search);
        } else {
            creationDate = null;
        }

        Specification<StudyEntity> creationDateSpec = null;
        if (creationDate != null) {
            creationDateSpec = (root, query, cb) -> cb.equal(root.get("creationDate"), creationDate);

        }
        return creationDateSpec;
    }

}
