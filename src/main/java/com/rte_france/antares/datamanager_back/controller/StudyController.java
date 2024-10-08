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
import org.springframework.web.bind.annotation.*;

import static com.rte_france.antares.datamanager_back.mapper.StudyMapper.toStudyPage;

@Slf4j
@RestController
@RequestMapping("/v1/study")
@RequiredArgsConstructor
public class StudyController {

    private static final String SORTING_CRITERION = "creationDate";
    private final StudyService studyService;

    @GetMapping("/search")
    public ResponseEntity<Page<StudyDTO>> getTrajectories(
            @RequestParam(value = "search", required = false, defaultValue = "") String search,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "20") Integer size) {

        Pageable paging = PageRequest.of(page-1, size, Sort.by(SORTING_CRITERION));

        return new ResponseEntity<>(toStudyPage(studyService.findStudiesByCriteria(search, paging)), HttpStatus.OK);
    }
}
