package com.rte_france.antares.datamanager_back.repository;


import com.rte_france.antares.datamanager_back.repository.model.ThermalCostTypeEntity;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:db/init_db.sql")
@Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "classpath:db/clean_db.sql")
class ThermalCostTypeRepositoryTest {

    @Autowired
    private ThermalCostTypeRepository thermalCostTypeRepository;

    @Autowired
    private TrajectoryRepository trajectoryRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findByFuelIgnoreCase_returnsEntitiesWhenExists() {
        String fuel = "GAS";

        // Record 1 in init_db.sql is fuel GAS
        Optional<ThermalCostTypeEntity> result = thermalCostTypeRepository.findByFuelIgnoreCase(fuel);

        assertThat(result).isNotEmpty();
        assertThat(result.get().getFuel()).isEqualTo(fuel);
    }

    @Test
    void findByFuelIgnoreCase_caseInsensitive_returnsEntities() {
        String fuel = "gas";

        Optional<ThermalCostTypeEntity> result = thermalCostTypeRepository.findByFuelIgnoreCase(fuel);

        assertThat(result).isNotEmpty();
        assertThat(result.get().getFuel()).isEqualTo("GAS");
    }

    @Test
    void findByFuelIgnoreCase_withSpaces_returnsEntitiesDueToTrim() {
        String fuel = "Gas";

        Optional<ThermalCostTypeEntity> result = thermalCostTypeRepository.findByFuelIgnoreCase(fuel);

        assertThat(result).isNotEmpty();
        assertThat(result.get().getFuel()).isEqualTo("GAS");
    }
}
