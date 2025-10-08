package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.ThermalSpecificParametersEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:db/init_db.sql")
@Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "classpath:db/clean_db.sql")
class ThermalSpecificParametersRepositoryTest {

    @Autowired
    private ThermalSpecificParametersRepository repository;


    @Test
    void findWithMrModulationByStudyIdAndHorizon_returnsMrSpecificEntities() {
        List<ThermalSpecificParametersEntity> result = repository.findWithMrModulationByStudyIdAndHorizon(1, "2025-2026");

        assertThat(result).isNotEmpty();
        assertThat(result).allMatch(entity -> entity.getMrSpecific() == 1);
    }

    @Test
    void findWithCmModulationByStudyIdAndHorizon_returnsCmSpecificEntities() {
        List<ThermalSpecificParametersEntity> result = repository.findWithCmModulationByStudyIdAndHorizon(1, "2025-2026");

        assertThat(result).isNotEmpty();
        assertThat(result).allMatch(entity -> entity.getCmSpecific() == 1);
    }

    @Test
    void findWithMrModulationByStudyIdAndHorizon_returnsEmptyForNonMatchingHorizon() {
        List<ThermalSpecificParametersEntity> result = repository.findWithMrModulationByStudyIdAndHorizon(1, "2030-2031");

        assertThat(result).isEmpty();
    }

    @Test
    void findWithCmModulationByStudyIdAndHorizon_returnsEmptyForNonMatchingStudyId() {
        List<ThermalSpecificParametersEntity> result = repository.findWithCmModulationByStudyIdAndHorizon(4, "2025-2026");

        assertThat(result).isEmpty();
    }
}