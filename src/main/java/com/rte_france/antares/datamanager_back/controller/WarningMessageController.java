package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.service.WarningMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/warnings")
@RequiredArgsConstructor
public class WarningMessageController {

    private final WarningMessageService warningMessageService;

    @PutMapping("/{id}/ack")
    public ResponseEntity<Void> acknowledgeWarning(@PathVariable Integer id) {
        warningMessageService.acknowledgeWarning(id);
        return ResponseEntity.ok().build();
    }
}