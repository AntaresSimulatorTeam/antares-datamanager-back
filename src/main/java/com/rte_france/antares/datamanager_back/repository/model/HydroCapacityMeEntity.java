package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity(name = "HydroCapacityMe")
@Table(name = "hydro_capacity_me")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HydroCapacityMeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hydro_capacity_me_seq_gen")
    @SequenceGenerator(name = "hydro_capacity_me_seq_gen", sequenceName = "hydro_capacity_me_sequence", allocationSize = 1)
    private Integer id;

    @Column(name = "node", length = 60, nullable = false)
    private String node;

    @Column(name = "reservoir_capacity")
    private BigDecimal reservoirCapacity;

    @Column(name = "generating_pmax_timestep", length = 20)
    private String generatingPmaxTimestep;

    @Column(name = "generating_pmax")
    private BigDecimal generatingPmax;

    @Column(name = "hours_at_generating_pmax")
    private BigDecimal hoursAtGeneratingPmax;

    @Column(name = "pumping_pmax_timestep", length = 20)
    private String pumpingPmaxTimestep;

    @Column(name = "pumping_pmax")
    private BigDecimal pumpingPmax;

    @Column(name = "hours_at_pumping_pmax")
    private BigDecimal hoursAtPumpingPmax;

    @ManyToOne(optional = false)
    @JoinColumn(name = "trajectory_id", nullable = false)
    private TrajectoryEntity trajectory;
}
