package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.dto.ProjectInputDto;
import com.rte_france.antares.datamanager_back.repository.ProjectRepository;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.model.ProjectEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;


@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {
    private final ProjectRepository projectRepository;
    private final StudyRepository studyRepository;

    @Override
    public ProjectEntity createProject(ProjectInputDto projectInputDto) {
        if (projectInputDto.getStudyIds().isEmpty())
            throw new IllegalArgumentException("A project must have at least one study.");
        Optional<ProjectEntity> existingProject = projectRepository.findByName(projectInputDto.getName());

        if (existingProject.isPresent()) {
            throw new IllegalArgumentException("A project with the same name already exists.");
        }

        ProjectEntity newProject = new ProjectEntity();
        newProject.setName(projectInputDto.getName());
        newProject.setCreationDate(Instant.now());
        newProject.setCreatedBy("pegase");
        List<String> studyIds = projectInputDto.getStudyIds().stream()
                .map(Object::toString)
                .toList();
        List<StudyEntity> studies = studyRepository.findAllById(studyIds);
        if (studies.size() != projectInputDto.getStudyIds().size()) {
            throw new IllegalArgumentException("Some studies do not exist.");
        }
        newProject.setStudies(studies);
        return projectRepository.save(newProject);
    }
}
