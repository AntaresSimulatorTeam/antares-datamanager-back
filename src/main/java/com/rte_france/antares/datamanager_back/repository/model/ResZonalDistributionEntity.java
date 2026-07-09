package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity(name = "ResZonalDistribution")
@Table(name = "res_zonal_distribution")
public class ResZonalDistributionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "res_zonal_distribution_seq_gen")
    @SequenceGenerator(name = "res_zonal_distribution_seq_gen", sequenceName = "res_zonal_distribution_sequence", allocationSize = 1)
    private Integer id;

    private String area;

    private String groupe;

    @Column(name = "pecd_zone")
    private String pecdZone;

    @Column(name = "capacity_by_year", precision = 4, scale = 2)
    private BigDecimal capacityByYear;

    @ManyToOne
    @JoinColumn(name = "trajectory_id")
    private TrajectoryEntity trajectory;

}

