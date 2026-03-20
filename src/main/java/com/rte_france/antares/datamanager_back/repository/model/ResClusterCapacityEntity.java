package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity(name = "ResClusterCapacity")
@Table(name = "res_cluster_capacity")
public class ResClusterCapacityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "res_cluster_capacity_seq_gen")
    @SequenceGenerator(name = "res_cluster_capacity_seq_gen", sequenceName = "res_cluster_capacity_sequence", allocationSize = 1)
    private Integer id;

    @Column(name = "to_use")
    private Boolean toUse;

    private String area;

    private String groupe;

    private String cluster;

    private String category;

    private String pecdZone;

    @Column(name = "capacity_by_year", precision = 4, scale = 2)
    private BigDecimal capacityByYear;

    @ManyToOne
    @JoinColumn(name = "trajectory_id")
    private TrajectoryEntity trajectory;

}
