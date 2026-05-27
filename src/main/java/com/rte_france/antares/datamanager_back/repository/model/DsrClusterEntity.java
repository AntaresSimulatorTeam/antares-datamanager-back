package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity(name = "DsrCluster")
@Table(name = "dsr_cluster")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DsrClusterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "dsr_cluster_seq_gen")
    @SequenceGenerator(name = "dsr_cluster_seq_gen", sequenceName = "dsr_cluster_sequence", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "to_use")
    private Boolean toUse;

    @Column(name = "area", length = 40)
    private String area;

    @Column(name = "name", length = 40)
    private String name;

    @Column(name = "capacity")
    private BigDecimal capacity;

    @Column(name = "nb_hour_per_day")
    private Integer nbHourPerDay;

    @Column(name = "max_hour_per_day")
    private Integer maxHourPerDay;

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "nb_units")
    private Integer nbUnits;

    @Column(name = "fo_rate")
    private BigDecimal foRate;

    @Column(name = "fo_duration")
    private Integer foDuration;

    @Column(name = "modulation")
    private Boolean modulation;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "trajectory_id", nullable = false)
    private TrajectoryEntity trajectory;
}

