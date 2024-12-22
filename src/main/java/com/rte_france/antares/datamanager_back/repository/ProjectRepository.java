package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.ProjectEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository extends JpaRepository<ProjectEntity, Integer> {

    Page<ProjectEntity> findAll(Specification<ProjectEntity> spec, Pageable pageable);

    List<ProjectEntity> findByNameContainingIgnoreCase(String partialName);


}
