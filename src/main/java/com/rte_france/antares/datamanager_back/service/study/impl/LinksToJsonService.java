package com.rte_france.antares.datamanager_back.service.study.impl;

import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.model.LinkEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
@Slf4j
@Service
public class LinksToJsonService {

    public void buildLinksDataMap(TrajectoryEntity trajectory, Map<String, Object> linksMap, StudyEntity study) {
        log.info("Links for trajectory={}, links count={}", trajectory.getFileName(), trajectory.getLinkEntities() != null ? trajectory.getLinkEntities().size() : 0);
        List<LinkEntity> linkEntityList = trajectory.getLinkEntities();
        boolean isStudyWithHvdc = study.getHvdc() != null && study.getHvdc();

        Map<String, Map<String, Object>> linksDataMap = linkEntityList.stream()
                .collect(Collectors.toMap(
                        linkEntity -> linkEntity.getName().replace("-", "/"),
                        linkEntity -> {
                            Map<String, Object> linkMap = linksMapGenerator();
                            linkMap.put("winterHpDirectMw", linkEntity.getWinterHpDirectMw());
                            linkMap.put("winterHpIndirectMw", linkEntity.getWinterHpIndirectMw());
                            linkMap.put("winterHcDirectMw", linkEntity.getWinterHcDirectMw());
                            linkMap.put("winterHcIndirectMw", linkEntity.getWinterHcIndirectMw());
                            linkMap.put("summerHpDirectMw", linkEntity.getSummerHpDirectMw());
                            linkMap.put("summerHpIndirectMw", linkEntity.getSummerHpIndirectMw());
                            linkMap.put("summerHcDirectMw", linkEntity.getSummerHcDirectMw());
                            linkMap.put("summerHcIndirectMw", linkEntity.getSummerHcIndirectMw());
                            linkMap.put("hurdleCost", linkEntity.getHurdleCost());
                            if (isStudyWithHvdc) {
                                linkMap.put("hvdcMwDirect", linkEntity.getHvdcMwDirect());
                                linkMap.put("hvdcMwIndirect", linkEntity.getHvdcMwIndirect());
                                linkMap.put("hvdcNbDirect", linkEntity.getHvdcNbDirect());
                                linkMap.put("hvdcNbIndirect", linkEntity.getHvdcNbIndirect());
                                linkMap.put("hvdcFoRateDirect", linkEntity.getHvdcFoRateDirect());
                                linkMap.put("hvdcFoRateIndirect", linkEntity.getHvdcFoRateIndirect());
                            }
                            return linkMap;
                        },
                        (existing, replacement) -> existing
                ));

        linksMap.putAll(linksDataMap);
        log.info("Numbers of links to create {}", linksDataMap.size());
    }

    private static Map<String, Object> linksMapGenerator() {
        Map<String, Object> linkMap = new HashMap<>();
        return linkMap;
    }


}
