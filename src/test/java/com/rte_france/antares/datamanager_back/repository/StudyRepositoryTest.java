package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlGroup;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;


@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@SqlGroup({
        @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:db/init_db.sql"),
        @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "classpath:db/clean_db.sql"),
})
class StudyRepositoryTest {

    @Autowired
    private StudyRepository studyRepository;

    @Test
    void findAll_returnsPageOfStudyEntities() {
        Pageable pageable = PageRequest.of(0, 5);
        Specification<StudyEntity> spec = Specification.where(null);

        Page<StudyEntity> page = studyRepository.findAll(spec, pageable);

        assertThat(page).isNotNull();
        assertThat(page.getContent()).isNotEmpty();
    }

    @Test
    void findAll_returnsEmptyPageForNonExistentStudyEntities() {
        Pageable pageable = PageRequest.of(0, 5);
        Specification<StudyEntity> spec = Specification.where((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("name"), "nonExistentStudy"));

        Page<StudyEntity> page = studyRepository.findAll(spec, pageable);

        assertThat(page).isNotNull();
        assertThat(page.getContent()).isEmpty();
    }
}
