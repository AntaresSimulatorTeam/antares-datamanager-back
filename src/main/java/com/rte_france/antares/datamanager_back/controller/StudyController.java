package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.dto.StudyDTO;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.service.StudyService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.rte_france.antares.datamanager_back.mapper.StudyMapper.toStudyPage;

@Slf4j
@RestController
@RequestMapping("/v1/study")
@RequiredArgsConstructor
public class StudyController {

    private static final String DEFAULT_SORT_COLUMN = "creationDate";
    private static final String DEFAULT_SORT_DIRECTION = "DESC";
    private final StudyService studyService;

    @GetMapping("/search")
    public ResponseEntity<Page<StudyDTO>> searchStudies(
            @RequestParam(value = "projectId", required = false, defaultValue = "") Integer projectId,
            @RequestParam(value = "search", required = false, defaultValue = "") String search,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(value = "sortColumn", required = false, defaultValue = DEFAULT_SORT_COLUMN) String sortColumn,
            @RequestParam(value = "sortDirection", required = false, defaultValue = DEFAULT_SORT_DIRECTION) String sortDirection) {

        Sort sort = Sort.by(
                Sort.Direction.fromString(sortDirection),
                sortColumn
        );
        Pageable paging = PageRequest.of(page - 1, size, sort);


        return new ResponseEntity<>(toStudyPage(studyService.findStudiesByCriteria(search, projectId, paging)), HttpStatus.OK);
    }


    @Operation(summary = "Search keywords by partial name for auto-completion")
    @GetMapping("/keywords/search")
    public ResponseEntity<List<String>> searchKeywordsByPartialName(@RequestParam String partialName) {
        return new ResponseEntity<>(studyService.searchKeywordsByPartialName(partialName), HttpStatus.OK);
    }


    @PostMapping
    public ResponseEntity<StudyDTO> createStudy(@RequestBody StudyDTO studyDTO) {
        StudyDTO createdStudy = studyService.createStudy(studyDTO);
        return new ResponseEntity<>(createdStudy, HttpStatus.CREATED);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStudyById(@PathVariable Integer id) {
        studyService.deleteStudyById(id);
        return ResponseEntity.noContent().build();
    }


    @PostMapping("/generate")
    public ResponseEntity<Void> generateStudy(@RequestParam Integer id) throws TechnicalException {
        studyService.generateStudy(id);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @Operation(summary = "Find a study by id")
    @GetMapping("/{id}")
    public ResponseEntity<StudyDTO> findStudyById(@PathVariable Integer id) {
        return new ResponseEntity<>(studyService.findStudyById(id), HttpStatus.OK);
    }

    @PostMapping("/duplicate")
    public ResponseEntity<StudyDTO> duplicateStudy(@RequestBody StudyDTO studyDTO) throws IOException {
        StudyDTO duplicatedStudy = studyService.duplicateStudy(studyDTO);
        return new ResponseEntity<>(duplicatedStudy, HttpStatus.CREATED);
    }

    @Operation(summary = "Update a study that is not generated yet (name, project name, keywords)")
    @PutMapping("/{id}")
    public ResponseEntity<StudyDTO> updateStudy(
            @PathVariable Integer id,
            @RequestBody StudyDTO studyDTO
    ) {
        StudyDTO updated = studyService.updateStudy(id, studyDTO);
        return ResponseEntity.ok(updated);
    }

}
