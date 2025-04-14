package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.dto.DefaultLoadDTO;
import com.rte_france.antares.datamanager_back.service.DefaultLoadService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/default_config")
public class DefaultLoadController {

    private final DefaultLoadService defaultLoadService;

    @Operation(summary = "Get load defaults values for IHM")
    @GetMapping(value = "/load")
    public ResponseEntity<List<DefaultLoadDTO>> fetchDefaultLoadConfig() {

        return ResponseEntity.ok(defaultLoadService.fetchAllDefaults());
    }
}
