package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.dto.ProjectDto;
import com.rte_france.antares.datamanager_back.dto.ProjectInputDto;
import com.rte_france.antares.datamanager_back.service.ProjectService;
import com.rte_france.antares.datamanager_back.service.impl.UserService;
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

import java.util.List;

import static com.rte_france.antares.datamanager_back.mapper.ProjectMapper.*;

@Slf4j
@RestController
@RequestMapping("/v1/project")
@RequiredArgsConstructor
public class ProjectController {

    private static final String SORTING_CRITERION = "creationDate";

    private final ProjectService projectService;

    private final UserService userService;

    @Operation(summary = "Get pinned projects by user")
    @GetMapping("/pinned")
    public ResponseEntity<List<ProjectDto>> getProjectsByUser(@RequestParam String userId) {
        return new ResponseEntity<>(toProjectDtos(projectService.getPinnedProjectsByUser(userId)), HttpStatus.OK);
    }

    @Operation(summary = "Unpin project for user")
    @PutMapping("/unpin")
    public void removePinnedProjectToUser(@RequestParam String userId, @RequestParam Integer projectId) {
        projectService.deletePinnedProjectForGivenUser(userId, projectId);
    }

    @Operation(summary = "Pin project for user")
    @PostMapping("/pin")
    public ResponseEntity<ProjectDto> pinProjectForUser(@RequestParam String userId, @RequestParam Integer projectId) {
        return new ResponseEntity<>(toProjectDto(projectService.pinProjectForUser(userId, projectId)), HttpStatus.OK);
    }

    @Operation(summary = "Search projects by criteria")
    @GetMapping("/search")
    public ResponseEntity<Page<ProjectDto>> searchProjects(
            @RequestParam(value = "search", required = false, defaultValue = "") String search,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "12") Integer size) {

        Pageable paging = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC,SORTING_CRITERION));
        return new ResponseEntity<>(toProjectPage(projectService.findProjectsByCriteria(search, paging)), HttpStatus.OK);
    }

    @Operation(summary = "Find project by Id")
    @GetMapping("/{id}")
    public ResponseEntity<ProjectDto> findProjectById(@PathVariable Integer id) {

        return new ResponseEntity<>(toProjectDto(projectService.findProjectById(id)), HttpStatus.OK);
    }

    @Operation(summary = "Delete project if it contains no studies")
    @DeleteMapping("/{id}")
    public void deleteProject(@PathVariable Integer id) {
        projectService.deleteProjectById(id);
    }

    @Operation(summary = "Search projects by partial name for auto-completion")
    @GetMapping("/autocomplete")
    public ResponseEntity<List<ProjectDto>> searchProjectsByName(@RequestParam String partialName) {
        return new ResponseEntity<>(projectService.searchProjectsByName(partialName), HttpStatus.OK);
    }

    @Operation(summary = "Create a new project")
    @PostMapping
    public ResponseEntity<ProjectDto> createProject(@RequestBody @NotNull ProjectInputDto projectInputDto) {

        return new ResponseEntity<>(toProjectDto(projectService.createProject(projectInputDto)), HttpStatus.OK);
    }
}
