package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity(name = "ResTechnologyDistribution")
@Table(name = "res_technology_distribution")
public class ResTechnologyDistributionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "res_technology_distribution_seq_gen")
    @SequenceGenerator(name = "res_technology_distribution_seq_gen", sequenceName = "res_technology_distribution_sequence", allocationSize = 1)
    private Integer id;

    private String area;

    private String groupe;

    private String cluster;

    @Column(name = "pecd_zone")
    private String pecdZone;

    @Column(name = "pecd_technology")
    private String pecdTechnology;

    @Column(name = "capacity_by_year")
    private Integer capacityByYear;

    @ManyToOne
    @JoinColumn(name = "trajectory_id")
    private TrajectoryEntity trajectory;

}

