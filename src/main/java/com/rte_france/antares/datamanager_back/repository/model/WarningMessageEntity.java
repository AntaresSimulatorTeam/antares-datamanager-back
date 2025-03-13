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
@Entity
@Table(name = "warning_message")
public class WarningMessageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "warning_seq_gen")
    @SequenceGenerator(name = "warning_seq_gen", sequenceName = "warning_sequence", allocationSize = 1)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WarningCode code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WarningLevel level;

    @Column(nullable = false)
    private String content;

    @ManyToOne
    @JoinColumn(name = "trajectory_id", nullable = false)
    private TrajectoryEntity trajectory;
}