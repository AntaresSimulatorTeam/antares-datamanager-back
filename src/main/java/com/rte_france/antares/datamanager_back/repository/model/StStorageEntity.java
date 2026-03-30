package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;

@Entity(name = "StStorage")
@Table(name = "st_storage")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StStorageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "st_storage_seq_gen")
    @SequenceGenerator(name = "st_storage_seq_gen", sequenceName = "st_storage_sequence", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "area", length = 10)
    private String area;

    @Column(name = "name", length = 40)
    private String name;

    @Column(name = "groupe", length = 40)
    private String groupe;

    @Column(name = "injection")
    private BigDecimal injection;

    @Column(name = "withdrawal")
    private BigDecimal withdrawal;

    @Column(name = "storage")
    private BigDecimal storage;

    @Column(name = "efficiency_injection")
    private BigDecimal efficiencyInjection;

    @Column(name = "efficiency_withdrawal")
    private BigDecimal efficiencyWithdrawal;

    @Column(name = "initial_level")
    private BigDecimal initialLevel;

    @Column(name = "initial_level_optim")
    private Boolean initialLevelOptim;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "series")
    private Boolean series;

    @Column(name = "st_constraints")
    private Boolean constraintsFlag;

    @Column(name="ts_path")
    String tsPath;

    @Column(name="constraints_path")
    String constraintsPath;


    @ManyToOne(optional = false)
    @JoinColumn(name = "trajectory_id", nullable = false)
    private TrajectoryEntity trajectory;
}