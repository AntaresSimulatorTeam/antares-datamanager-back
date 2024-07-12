package com.rte_france.antares.datamanager_back.controller;


import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/v1/area")
@RequiredArgsConstructor
public class AreaController {
    @Operation(summary = "First api", description = "say Hello :)")
    @GetMapping
    public String sayHelloPegase() {
        log.info("a simple controller area");
        return "hello area";
    }
}
