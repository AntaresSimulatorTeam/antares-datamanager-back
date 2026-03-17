package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.repository.model.ResTypeEntity;
import com.rte_france.antares.datamanager_back.service.res.ResTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/trajectory")
@RequiredArgsConstructor
public class ResController {

    private final ResTypeService resTypeService;

    @GetMapping("res-types")
    public ResponseEntity<List<ResTypeEntity>> getAllResTypes() {
        return ResponseEntity.ok(resTypeService.getAllResTypes());
    }
}

