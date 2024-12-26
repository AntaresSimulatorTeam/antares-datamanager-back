package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.dto.StudyDTO;
import com.rte_france.antares.datamanager_back.service.StudyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.rte_france.antares.datamanager_back.mapper.StudyMapper.toStudyPage;

@Slf4j
@RestController
@RequestMapping("/v1/study")
@RequiredArgsConstructor
public class StudyController {

    private static final String SORTING_CRITERION = "creationDate";
    private final StudyService studyService;

    @GetMapping("/search")
    public ResponseEntity<Page<StudyDTO>> searchStudies(
            @RequestParam(value = "projectId", required = false, defaultValue = "") Integer projectId,
            @RequestParam(value = "search", required = false, defaultValue = "") String search,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(value = "sortColumn", required = false) String sortColumn,
            @RequestParam(value = "sortDirection", required = false) String sortDirection) {

        Sort sorting = Sort.by(SORTING_CRITERION);

        if (sortColumn != null && !sortColumn.isEmpty() && !sortDirection.isEmpty()) {
            Sort.Direction direction = Sort.Direction.fromString(sortDirection);
            sorting = Sort.by(direction, sortColumn);
        }
        Pageable paging = PageRequest.of(page - 1, size, sorting);

        return new ResponseEntity<>(toStudyPage(studyService.findStudiesByCriteria(search, projectId, paging)), HttpStatus.OK);
    }
}
