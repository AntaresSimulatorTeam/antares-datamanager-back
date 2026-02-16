package com.rte_france.antares.datamanager_back.service.study.impl;

import com.rte_france.antares.datamanager_back.repository.model.LinkEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

//@Slf4j
//@Service
//public class DsrToJsonService {


 //   private static final String PROPERTIES = "properties";
  //  private static final String MATRIX_HASH = "matrix hash";


 //   public void buildDsrDataMap(TrajectoryEntity trajectory, Map<String, Object> dsrMap) {
        //log.info("DSR for trajectory={}", trajectory.getFileName(), trajectory.getDsrClusterEntities() != null ? trajectory.getDsrClusterEntities().size() : 0);
        //List<LinkEntity> linkEntityList = trajectory.getLinkEntities();

//        Map<String, Map<String, Object>> dsrMap = dsrClusterEntities.stream()
//                .collect(Collectors.toMap(
//
//                dsrClusterEntity -> {
//                            Map<String, Object> linkMap = dsrMapGenerator();
//                            linkMap.put("winterHpDirectMw", linkEntity.getWinterHpDirectMw());
//                            linkMap.put("winterHpIndirectMw", linkEntity.getWinterHpIndirectMw());
//                            linkMap.put("winterHcDirectMw", linkEntity.getWinterHcDirectMw());
//                            linkMap.put("winterHcIndirectMw", linkEntity.getWinterHcIndirectMw());
//                            linkMap.put("summerHpDirectMw", linkEntity.getSummerHpDirectMw());
//                            linkMap.put("summerHpIndirectMw", linkEntity.getSummerHpIndirectMw());
//                            linkMap.put("summerHcDirectMw", linkEntity.getSummerHcDirectMw());
//                            linkMap.put("summerHcIndirectMw", linkEntity.getSummerHcIndirectMw());
//                            linkMap.put("hurdleCost", linkEntity.getHurdleCost());
//                            return linkMap;
//                        },
//                        (existing, replacement) -> existing
//                ));
//
//        linksMap.putAll(linksDataMap);
//        log.info("Numbers of links to create {}", linksDataMap.size());
//    }
//
//    private static Map<String, Object> dsrMapGenerator() {
//        Map<String, Object> dsrMap = new HashMap<>();
//        dsrMap.put(PROPERTIES, "LinkProperties as JSON");
//        dsrMap.put("ui", "LinkUi class as JSON");
//        dsrMap.put("parameters", MATRIX_HASH);
//        dsrMap.put("capacity_direct", MATRIX_HASH);
//        dsrMap.put("capacity_indirect", MATRIX_HASH);
//
//        return dsrMap;
//    }
//

//}
