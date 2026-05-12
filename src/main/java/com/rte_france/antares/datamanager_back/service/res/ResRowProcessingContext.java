package com.rte_france.antares.datamanager_back.service.res;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import lombok.Getter;
import lombok.Builder;

import java.util.List;

@Getter
@Builder
public class ResRowProcessingContext {
    private final List<String> studyAreas;
    private final String areaParam;
    private int yearColIndex;
    private final String trajectoryToUse;
    private String technology;
    private final TrajectoryType trajectoryType;
}
