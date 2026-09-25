package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "ThermalMe")
@Table(name = "thermal_me")
public class ThermalMeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "thermal_me_seq_gen")
    @SequenceGenerator(name = "thermal_me_seq_gen", sequenceName = "thermal_me_sequence", allocationSize = 1)
    private Integer id;

    @Column(nullable = false, length = 60)
    private String node;

    @Column(name = "group_name", length = 20)
    private String groupName;

    @Column(name = "cluster_name", length = 60)
    private String clusterName;
    
    private Boolean enabled;

    @Column(name = "nominal_capacity")
    private Double nominalCapacity;

    @Column(name = "nb_unit")
    private Integer nbUnit;

    @Column(name = "marginal_cost")
    private Double marginalCost;

    @Column(name = "marginal_cost_timestep", length = 10)
    private String marginalCostTimestep;

    @Column(name = "marginal_cost_modulation")
    private Integer marginalCostModulation;
    
    @Column(name = "market_bid_cost")
    private Double marketBidCost;

    @Column(name = "market_bid_cost_timestep", length = 10)
    private String marketBidCostTimestep;

    @Column(name = "market_bid_cost_modulation")
    private Integer marketBidCostModulation;

    @Column(name = "mr_modulation")
    private Integer mrModulation;

    @Column(name = "must_run", length = 10)
    private Boolean mustRun;

    @Column(name = "mr_timestep", length = 10)
    private String mrTimestep;

    @Column(name = "cm_modulation")
    private Integer cmModulation;

    @Column(name = "cm_timestep", length = 10)
    private String cmTimestep;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trajectory_id")
    private TrajectoryEntity trajectory;

}
