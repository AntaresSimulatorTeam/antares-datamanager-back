package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "cluster")
public class ClusterEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cluster_seq_gen")
    @SequenceGenerator(name = "cluster_seq_gen", sequenceName = "cluster_sequence", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "type_cluster", nullable = false, length = 20, unique = true)
    private String typeCluster;
}

