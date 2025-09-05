package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.ThermalGroupMappingEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@DataJpaTest
class ThermalGroupMappingRepositoryTest {

    @Autowired
    private ThermalGroupMappingRepository repository;

    @Autowired
    private TestEntityManager em;

    @Test
    void findByClusterIgnoreCase_isCaseInsensitive() {
        var e = ThermalGroupMappingEntity.builder()
                .cluster("conventional old 1")
                .groupName("Gas")
                .build();
        em.persistAndFlush(e);

        var lower = repository.findByClusterIgnoreCase("conventional old 1");
        var upper = repository.findByClusterIgnoreCase("CONVENTIONAL OLD 1");

        assertThat(lower).isPresent();
        assertThat(lower.map(ThermalGroupMappingEntity::getGroupName)).contains("Gas");
        assertThat(upper).isPresent();
    }

    @Test
    void findByClusterIgnoreCase_returnsEmpty_whenNoMatch() {
        var result = repository.findByClusterIgnoreCase("does_not_exist");
        assertThat(result).isEmpty();
    }
}
