package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity(name = "EfficiencyMe")
@Table(name = "efficiency_me")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EfficiencyMeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "efficiency_me_seq_gen")
    @SequenceGenerator(name = "efficiency_me_seq_gen", sequenceName = "efficiency_me_sequence", allocationSize = 1)
    private Integer id;

    @Column(name = "node_cluster", length = 60, nullable = false)
    private String nodeCluster;

    @Column(name = "type", length = 10)
    private String type;

    @Column(name = "comments", length = 200)
    private String comments;

    @Column(name = "efficiency", nullable = false)
    private BigDecimal efficiency;

    @ManyToOne(optional = false)
    @JoinColumn(name = "trajectory_id", nullable = false)
    private TrajectoryEntity trajectory;
}
