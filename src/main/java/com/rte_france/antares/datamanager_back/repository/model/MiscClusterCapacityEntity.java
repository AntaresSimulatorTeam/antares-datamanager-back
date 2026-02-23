package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity(name = "MiscClusterCapacity")
@Table(name = "misc_cluster_capacity")
public class MiscClusterCapacityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "misc_cluster_capacity_seq_gen")
    @SequenceGenerator(name = "misc_cluster_capacity_seq_gen", sequenceName = "misc_cluster_capacity_sequence", allocationSize = 1)
    private Integer id;

    @Column(name = "to_use")
    private Boolean toUse;

    private String area;

    private String groupe;

    private String cluster;

    private String category;

    @Column(name = "capacity_by_year")
    private BigDecimal capacityByYear;

    @ManyToOne
    @JoinColumn(name = "trajectory_id")
    private TrajectoryEntity trajectory;

}

