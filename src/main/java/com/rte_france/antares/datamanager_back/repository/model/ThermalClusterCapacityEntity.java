package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;


@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "thermal_cluster_capacity")
public class ThermalClusterCapacityEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "thermal_cluster_capacity_seq_gen")
    @SequenceGenerator(name = "thermal_cluster_capacity_seq_gen", sequenceName = "tthermal_cluster_capacity_sequence", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Size(max = 255)
    @Column(name = "scenario")
    private String scenario;

    @Size(max = 255)
    @Column(name = "default_scenario")
    private Boolean defaultScenario;

    @Size(max = 255)
    @Column(name = "name")
    private String name;

    @Size(max = 255)
    @Column(name = "month_year")
    private String monthYear;

    @Column(name = "category")
    @Enumerated(EnumType.STRING)
    private ThermalCategoryEnum category;

    @Column(name = "capacity")
    private Double value;

    @Column(name = "to_use")
    private Boolean toUse;


    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "trajectory_id")
    private TrajectoryEntity trajectory;


}