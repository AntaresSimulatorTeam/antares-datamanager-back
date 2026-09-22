package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "hydro_parameters_me", indexes = {
        @Index(name = "idx_hydro_param_trajectory", columnList = "trajectory_id"),
        @Index(name = "idx_hydro_param_node", columnList = "node")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HydroParametersMeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trajectory_id", nullable = false)
    private Integer trajectoryId;

    @Column(name = "node", nullable = false, length = 60)
    private String node;

    @Column(name = "inter_monthly_correlation")
    private BigDecimal interMonthlyCorrelation;

    @Column(name = "inter_daily_breakdown")
    private BigDecimal interDailyBreakdown;

    @Column(name = "intra_daily_modulation")
    private BigDecimal intraDailyModulation;

    @Column(name = "inter_monthly_breakdown")
    private BigDecimal interMonthlyBreakdown;

    @Column(name = "initialize_reservoir_date")
    private Integer initializeReservoirDate;

    @Column(name = "leeway_low")
    private BigDecimal leewayLow;

    @Column(name = "leeway_up")
    private BigDecimal leewayUp;

    @Column(name = "pumping_efficiency")
    private BigDecimal pumpingEfficiency;

    @Column(name = "reservoir_management")
    private Boolean reservoirManagement;

    @Column(name = "follow_load")
    private Boolean followLoad;

    @Column(name = "use_heuristic")
    private Boolean useHeuristic;

    @Column(name = "use_water")
    private Boolean useWater;

    @Column(name = "hard_bounds")
    private Boolean hardBounds;

    @Column(name = "use_leeway")
    private Boolean useLeeway;

    @Column(name = "power_to_level")
    private Boolean powerToLevel;

}
