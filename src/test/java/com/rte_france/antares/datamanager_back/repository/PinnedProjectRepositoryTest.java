package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.PinnedProjectEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlGroup;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@SqlGroup({
        @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:db/init_db.sql"),
        @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "classpath:db/clean_db.sql"),
})
class PinnedProjectRepositoryTest {

    @Autowired
    private PinnedProjectRepository pinnedProjectRepository;

    @Test
    void findById_Nni_returnsEntitiesWhenExist() {
        String nni = "me00247";

        List<PinnedProjectEntity> result = pinnedProjectRepository.findById_Nni(nni);

        assertThat(result).isNotEmpty().allMatch(entity -> entity.getId().getNni().equals(nni));
    }

    @Test
    void findById_Nni_returnsEmptyWhenNoneExist() {
        String nni = "nonExistentUser";

        List<PinnedProjectEntity> result = pinnedProjectRepository.findById_Nni(nni);

        assertThat(result).isEmpty();
    }
}
