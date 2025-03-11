package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
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

    @ManyToOne
    @JoinColumn(name = "trajectory_id", nullable = false)
    private TrajectoryEntity trajectory;

    @ManyToOne
    @JoinColumn(name = "study_id", nullable = false)
    private StudyEntity study;



}