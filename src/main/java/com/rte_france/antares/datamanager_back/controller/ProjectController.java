package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.dto.ProjectDto;

import com.rte_france.antares.datamanager_back.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.rte_france.antares.datamanager_back.mapper.ProjectMapper.toProjectDtos;

@Slf4j
@RestController
@RequestMapping("/v1/project")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @Operation(summary = "Get pinned projects by user")
    @GetMapping("/pinned")
    public ResponseEntity<List<ProjectDto>> getProjectsByUser(@RequestParam String userId) {
        return new ResponseEntity<>(toProjectDtos(projectService.getPinnedProjectsByUser(userId)), HttpStatus.OK);
    }
}
