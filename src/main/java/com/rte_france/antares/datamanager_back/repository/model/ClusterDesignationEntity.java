package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "cluster_designation")
public class ClusterDesignationEntity {
    @EmbeddedId
    private ClusterDesignationKey id;

    @MapsId("clusterId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cluster_id", nullable = false)
    private ClusterEntity cluster;
}

