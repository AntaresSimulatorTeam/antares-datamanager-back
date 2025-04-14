package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.DefaultLoadEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DefaultLoadRepository extends JpaRepository<DefaultLoadEntity, Integer> {

    List<DefaultLoadEntity> findAllByIsDefaultIsTrue();
}
