package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;

@BatchSize(size = 1000)
@Getter
@Setter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "nuclear_modulation_parameter")
public class NuclearModulationParameterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "nuclear_modulation_parameter_seq")
    @SequenceGenerator(name = "nuclear_modulation_parameter_seq", sequenceName = "nuclear_modulation_parameter_sequence", allocationSize = 1)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "trajectory_id", nullable = false)
    private TrajectoryEntity trajectory;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "value", nullable = false)
    private BigDecimal value;
}

