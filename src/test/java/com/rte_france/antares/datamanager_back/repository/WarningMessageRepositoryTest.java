package com.rte_france.antares.datamanager_back.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:db/init_db.sql")
@Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "classpath:db/clean_db.sql")
class WarningMessageRepositoryTest {

    @Autowired
    private WarningMessageRepository warningMessageRepository;

    @Test
    void existsByWarningContentAndTrajectoryIdAndStudyId_returnsTrueWhenExists() {
        String warningContent = "Area A is not linked";
        Integer trajectoryId = 1;
        Integer studyId = 1;

        boolean result = warningMessageRepository.existsByWarningContentAndTrajectoryIdAndStudyId(warningContent, trajectoryId, studyId);

        assertThat(result).isTrue();
    }

    @Test
    void existsByWarningContentAndTrajectoryIdAndStudyId_returnsFalseWhenDoesNotExist() {
        String warningContent = "Non-existent warning content";
        Integer trajectoryId = 999;
        Integer studyId = 999;

        boolean result = warningMessageRepository.existsByWarningContentAndTrajectoryIdAndStudyId(warningContent, trajectoryId, studyId);

        assertThat(result).isFalse();
    }
}