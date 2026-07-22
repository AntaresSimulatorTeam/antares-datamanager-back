package com.rte_france.antares.datamanager_back.dto;

import java.math.BigDecimal;

public record NuclearConstraintItemDTO(
        String name,
        String type,
        BigDecimal coeff,
        boolean includesPeak,
        String series
) {}