package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

@Entity(name = "HydroParameters")
@Table(name = "hydro_parameters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HydroParametersEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hydro_parameters_seq_gen")
    @SequenceGenerator(name = "hydro_parameters_seq_gen", sequenceName = "hydro_parameters_sequence", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "trajectory_id", nullable = false)
    private TrajectoryEntity trajectory;

    @Column(name = "node", length = 20)
    private String node;

    @Column(name = "inter_daily_breakdown")
    private Integer interDailyBreakdown;

    @Column(name = "inter_daily_modulation")
    private Integer interDailyModulation;

    @Column(name = "inter_monthly_breakdown")
    private Integer interMonthlyBreakdown;

    @Column(name = "initialize_reservoir_date")
    private Integer initializeReservoirDate;

    @Column(name = "pumping_efficiency")
    private Integer pumpingEfficiency;

    @Column(name = "reservoir")
    private Boolean reservoir;

    @Column(name = "reservoir_capacity")
    private Number reservoirCapacity;

    @Column(name = "follow_load")
    private Boolean followLoad;

    @Column(name = "use_water")
    private Boolean useWater;
}
