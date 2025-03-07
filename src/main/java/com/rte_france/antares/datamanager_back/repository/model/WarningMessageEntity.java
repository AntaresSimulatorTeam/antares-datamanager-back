package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "warning_messages")
public class WarningMessageEntity {
    @Id
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