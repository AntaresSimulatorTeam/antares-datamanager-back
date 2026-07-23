package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.ClusterDesignationEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@DataJpaTest
class ClusterDesignationRepositoryTest {

    @Autowired
    private ClusterDesignationRepository repository;

    @Test
    void findByCluster_TypeCluster_returnsCp0Cp1Cp2Designations() {
        List<ClusterDesignationEntity> result = repository.findByCluster_TypeCluster("cp0_cp1_cp2");

        List<String> unitNames = result.stream().map(e -> e.getId().getNomCluster()).toList();

        assertThat(unitNames)
                .contains("BLAYAN01", "PENLYN01", "CHOO2N01", "CIVAUN01", "PALUEN01", "FLAMAN01", "FLAMAN02")
                .doesNotContain("FLAMAN03", "EPR01");
    }

    @Test
    void findByCluster_TypeCluster_returnsEprDesignations() {
        List<ClusterDesignationEntity> result = repository.findByCluster_TypeCluster("EPR");

        List<String> unitNames = result.stream().map(e -> e.getId().getNomCluster()).toList();

        assertThat(unitNames).contains("FLAMAN03", "EPR01", "EPR24");
    }

    @Test
    void findByCluster_TypeCluster_returnsEmpty_whenTypeUnknown() {
        assertThat(repository.findByCluster_TypeCluster("does_not_exist")).isEmpty();
    }
}
