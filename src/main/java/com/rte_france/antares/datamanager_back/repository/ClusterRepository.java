package com.rte_france.antares.datamanager_back.repository;
import com.rte_france.antares.datamanager_back.repository.model.ClusterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
@Repository
public interface ClusterRepository extends JpaRepository<ClusterEntity, Integer> {
}
