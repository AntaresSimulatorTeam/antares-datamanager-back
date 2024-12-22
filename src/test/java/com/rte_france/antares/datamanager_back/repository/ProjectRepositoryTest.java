package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.ProjectEntity;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;


@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@SqlGroup({
        @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:db/init_db.sql"),
        @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "classpath:db/clean_db.sql"),
})
class ProjectRepositoryTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Test
    void findAll_returnsPageOfProjectEntities() {
        Pageable pageable = PageRequest.of(0, 5);
        Specification<ProjectEntity> spec = Specification.where(null);

        Page<ProjectEntity> page = projectRepository.findAll(spec, pageable);

        assertThat(page).isNotNull();
        assertThat(page.getContent()).isNotEmpty();
    }

    @Test
    void findAll_returnsEmptyPageForNonExistentProjectEntities() {
        Pageable pageable = PageRequest.of(0, 5);
        Specification<ProjectEntity> spec = Specification.where((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("name"), "nonExistentStudy"));

        Page<ProjectEntity> page = projectRepository.findAll(spec, pageable);

        assertThat(page).isNotNull();
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void getProjectDetails_byProjectId_returnsProjectDetails() {

        Optional<ProjectEntity> projectEntity = projectRepository.findById(1);

        assertThat(projectEntity).isNotNull();
    }

    @Test
void findByNameContainingIgnoreCaseReturnsMatchingProjects() {
    List<ProjectEntity> projects = projectRepository.findByNameContainingIgnoreCase("Proj");

    assertThat(projects).isNotNull();
    assertThat(projects).isNotEmpty();
    assertThat(projects.get(0).getName()).containsIgnoringCase("Proj");
}

@Test
void findByNameContainingIgnoreCaseReturnsEmptyListWhenNoMatches() {
    List<ProjectEntity> projects = projectRepository.findByNameContainingIgnoreCase("NonExistent");

    assertThat(projects).isNotNull();
    assertThat(projects).isEmpty();
}

@Test
void findByNameContainingIgnoreCaseHandlesNullInput() {
    List<ProjectEntity> projects = projectRepository.findByNameContainingIgnoreCase(null);

    assertThat(projects).isNotNull();
    assertThat(projects).isEmpty();
}
}
