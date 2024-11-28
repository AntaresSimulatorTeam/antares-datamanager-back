package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

public interface StudyRepository extends JpaRepository<StudyEntity, Integer> {

    Page<StudyEntity> findAll(Specification<StudyEntity> spec, Pageable pageable);

    List<StudyEntity> findStudyEntitiesByProjectId(@RequestParam Integer projectId);


}
