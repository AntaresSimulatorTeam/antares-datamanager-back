package com.rte_france.antares.datamanager_back.repository;

 import com.rte_france.antares.datamanager_back.repository.model.ThermalCostTypeEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlGroup;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@SqlGroup({
        @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:db/init_db.sql"),
        @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "classpath:db/clean_db.sql"),
})
class ThermalCostTypeRepositoryTest {

    @Autowired
    private ThermalCostTypeRepository thermalCostTypeRepository;

    @Test
    void findThermalCostTypeEntityByFuelAndCountry_returnsEntityWhenExists() {
        String fuel = "OIL";
        String country = "europe";

        Optional<ThermalCostTypeEntity> result = thermalCostTypeRepository.findThermalCostTypeEntityByFuelAndCountry(fuel, country);

        assertThat(result).isNotEmpty();
        assertThat(result.get().getFuel()).isEqualTo(fuel);
        assertThat(result.get().getCountry()).isEqualTo(country);
    }

    @Test
    void findThermalCostTypeEntityByFuelAndCountry_returnsEmptyWhenDoesNotExist() {
        String fuel = "OIL";
        String country = "ARGENTINE";

        Optional<ThermalCostTypeEntity> result = thermalCostTypeRepository.findThermalCostTypeEntityByFuelAndCountry(fuel, country);

        assertThat(result).isEmpty();
    }
}
