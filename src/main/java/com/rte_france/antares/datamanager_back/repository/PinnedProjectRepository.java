package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.PinnedProjectEntity;
import com.rte_france.antares.datamanager_back.repository.model.PinnedProjectEntityId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface PinnedProjectRepository extends JpaRepository<PinnedProjectEntity, PinnedProjectEntityId> {

    List<PinnedProjectEntity> findById_Nni(String nni);

    @Transactional
    @Modifying
    void deletePinnedProjectEntityById(PinnedProjectEntityId pinnedProjectEntityId);

}