package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.ClusterDesignationEntity;
import com.rte_france.antares.datamanager_back.repository.model.ClusterDesignationKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClusterDesignationRepository extends JpaRepository<ClusterDesignationEntity, ClusterDesignationKey> {
}

