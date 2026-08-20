package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity(name = "ScenarioBuilder")
@Table(name = "scenario_builder")
public class ScenarioBuilderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "scenario_builder_seq_gen")
    @SequenceGenerator(name = "scenario_builder_seq_gen", sequenceName = "scenario_builder_seq", allocationSize = 1)
    private Integer id;

    @Column(name = "data")
    private String data;

    @ManyToOne
    @JoinColumn(name = "trajectory_id")
    private TrajectoryEntity trajectory;
}
