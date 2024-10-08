package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.dto.ProjectDto;
import com.rte_france.antares.datamanager_back.dto.ProjectInputDto;
import com.rte_france.antares.datamanager_back.dto.StudyDTO;
import com.rte_france.antares.datamanager_back.mapper.ProjectMapper;
import com.rte_france.antares.datamanager_back.service.ProjectService;
import com.rte_france.antares.datamanager_back.service.StudyService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.rte_france.antares.datamanager_back.mapper.ProjectMapper.toProjectDto;
import static com.rte_france.antares.datamanager_back.mapper.StudyMapper.toStudyPage;

@Slf4j
@RestController
@RequestMapping("/v1/project")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @Operation(summary = "Create a new project")
    @PostMapping
    public ResponseEntity<ProjectDto> createProject(@RequestBody @NotNull  ProjectInputDto projectInputDto) {

        return new ResponseEntity<>(toProjectDto(projectService.createProject(projectInputDto)), HttpStatus.OK);
    }
}
