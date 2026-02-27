package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.MiscLoadFactoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MiscLoadFactoryRepository extends JpaRepository<MiscLoadFactoryEntity, Integer> {
}

