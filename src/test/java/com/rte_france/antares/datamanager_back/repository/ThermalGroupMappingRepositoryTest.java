package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.ThermalGroupMappingEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@DataJpaTest
class ThermalGroupMappingRepositoryTest {

    @Autowired
    private ThermalGroupMappingRepository repository;

    @Autowired
    private org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager em;

    @Test
    void findBySourceValueIgnoreCase_returnsHit_caseInsensitive() {
        var e = ThermalGroupMappingEntity.builder()
                .sourceValue("conventional old 1")
                .pemmdbGroup("Gas")
                .build();
        em.persistAndFlush(e);

        Optional<ThermalGroupMappingEntity> test1 = repository.findBySourceValueIgnoreCase("conventional old 1");
        Optional<ThermalGroupMappingEntity> test2 = repository.findBySourceValueIgnoreCase("CONVENTIONAL OLD 1");

        assertThat(test1).isPresent();
        assertThat(test1.get().getPemmdbGroup()).isEqualTo("Gas");
        assertThat(test2).isPresent();
    }

    @Test
    void findBySourceValueIgnoreCase_returnsEmpty_whenNoMatch() {
        assertThat(repository.findBySourceValueIgnoreCase("no_match_test")).isEmpty();
    }
}
