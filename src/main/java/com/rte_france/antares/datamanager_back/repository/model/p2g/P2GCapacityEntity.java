package com.rte_france.antares.datamanager_back.repository.model.p2g;

import com.rte_france.antares.datamanager_back.repository.model.StConstraintsParameterEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity(name = "P2GCapacity")
@Table(name = "p2g_capacity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class P2GCapacityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "p2g_capacity_seq_gen")
    @SequenceGenerator(name = "p2g_capacity_seq_gen", sequenceName = "p2g_capacity_seq", allocationSize = 1)
    private Integer id;

    private String area;

    @Column(name = "base_fatal_band", nullable = true)
    private Double baseFatalBand;
    
    @Column(name = "base_eff", nullable = true)
    private Double baseEff;

    @Column(name = "base_capacity", nullable = true)
    private Double baseCapacity;

    @Column(name = "marg_capacity", nullable = true)
    private Double margCapacity;

    @Column(name = "methanation_capacity", nullable = true)
    private Double methanationCapacity;

    @Column(name = "asservi_capacity", nullable = true)
    private Double asserviCapacity;

    @ManyToOne
    @JoinColumn(name = "trajectory_id")
    private TrajectoryEntity trajectory;
}
