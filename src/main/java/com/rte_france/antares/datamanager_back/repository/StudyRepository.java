package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface StudyRepository extends JpaRepository<StudyEntity, String> {

    Page<StudyEntity> findAll(Specification<StudyEntity> spec, Pageable pageable);

    @Query("SELECT DISTINCT t FROM StudyEntity s JOIN s.tags t WHERE t IN :tags")
    List<String> findExistingTags(@Param("tags") List<String> tags);

    @Query("SELECT DISTINCT tag FROM StudyEntity s JOIN s.tags tag WHERE tag LIKE %:partialName%")
    List<String> findKeywordsByPartialName(String partialName);


    boolean existsByNameAndProjectName(String name, String projectName);
}
