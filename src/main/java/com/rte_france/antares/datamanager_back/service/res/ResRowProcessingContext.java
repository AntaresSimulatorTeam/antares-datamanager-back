package com.rte_france.antares.datamanager_back.service.res;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public class ResRowProcessingContext {
    private final List<String> studyAreas;
    private final String areaParam;
    private final int yearColIndex;
    private final String trajectoryToUse;
    private final String technology;
    private final TrajectoryType trajectoryType;
}
