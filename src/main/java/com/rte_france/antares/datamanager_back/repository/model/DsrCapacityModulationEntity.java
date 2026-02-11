package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity(name = "DsrCapacityModulation")
@Table(name = "dsr_capacity_modulation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DsrCapacityModulationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "dsr_capacity_modulation_seq_gen")
    @SequenceGenerator(name = "dsr_capacity_modulation_seq_gen", sequenceName = "dsr_capacity_modulation_sequence", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "trajectory_id", nullable = false)
    private TrajectoryEntity trajectory;

    @Column(name = "date_time")
    private LocalDateTime dateTime;

    @Column(name = "area", length = 40)
    private String area;

    @Column(name = "area_cluster_name", length = 40)
    private String clusterName;

    @Column(name = "capacity_value")
    private BigDecimal capacityValue;
}

