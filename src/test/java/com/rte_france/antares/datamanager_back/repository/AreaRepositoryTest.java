package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
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
class AreaRepositoryTest {

    @Autowired
    private AreaRepository areaRepository;

    @Test
    void findAreaByName_returnsEntityWhenExists() {
        String name = "area1";

        Optional<AreaEntity> result = areaRepository.findAreaByName(name);

        assertThat(result).isNotEmpty();
        assertThat(result.get().getName()).isEqualTo(name);
    }

    @Test
    void findAreaByName_returnsEmptyWhenDoesNotExist() {
        String name = "nonExistentArea";

        Optional<AreaEntity> result = areaRepository.findAreaByName(name);

        assertThat(result).isEmpty();
    }
}
