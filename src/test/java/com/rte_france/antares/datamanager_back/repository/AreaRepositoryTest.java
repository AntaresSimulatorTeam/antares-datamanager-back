package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:db/init_db.sql")
@Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "classpath:db/clean_db.sql")
class AreaRepositoryTest {

    @Autowired
    private AreaRepository areaRepository;

    @Test
    void findAreaByName_returnsEntityWhenExists() {
        String name = "area8";

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

    @Test
    void findAreaByNameAndStudyId_returnsEntityWhenExists() {
        String areaName = "area8";
        Integer studyId = 1;

        Optional<AreaEntity> result = areaRepository.findAreaByNameAndStudyId(areaName, studyId);

        assertThat(result).isNotEmpty();
        assertThat(result.get().getName()).isEqualTo(areaName);
    }

    @Test
    void findAreaByNameAndStudyId_returnsEmptyWhenAreaDoesNotExist() {
        String areaName = "nonExistentArea";
        Integer studyId = 1;

        Optional<AreaEntity> result = areaRepository.findAreaByNameAndStudyId(areaName, studyId);

        assertThat(result).isEmpty();
    }

    @Test
    void findAreaByNameAndStudyId_returnsEmptyWhenStudyDoesNotExist() {
        String areaName = "area8";
        Integer studyId = 999;

        Optional<AreaEntity> result = areaRepository.findAreaByNameAndStudyId(areaName, studyId);

        assertThat(result).isEmpty();
    }
}
