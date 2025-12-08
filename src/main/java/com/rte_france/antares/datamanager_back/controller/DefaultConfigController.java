package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.dto.DefaultLoadDTO;
import com.rte_france.antares.datamanager_back.dto.DefaultThermalTechnologyDTO;
import com.rte_france.antares.datamanager_back.service.common.DefaultConfigService;
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
public class DefaultConfigController {

    private final DefaultConfigService defaultConfigService;

    @Operation(summary = "Get load defaults values for IHM")
    @GetMapping(value = "/load")
    public ResponseEntity<List<DefaultLoadDTO>> fetchDefaultLoadConfig() {

        return ResponseEntity.ok(defaultConfigService.fetchAllDefaults());
    }


    @Operation(summary = "Get thermal technology defaults values for IHM")
    @GetMapping(value = "/thermal-technology-display")
    public ResponseEntity<List<DefaultThermalTechnologyDTO>> fetchDefaultThermalTechnologyToDisplay() {

        return ResponseEntity.ok(defaultConfigService.fetchAllThermalTechnologies());
    }
}
