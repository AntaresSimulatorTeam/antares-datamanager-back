package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.WarningMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarningMessageRepository extends JpaRepository<WarningMessageEntity, Integer> {
}
