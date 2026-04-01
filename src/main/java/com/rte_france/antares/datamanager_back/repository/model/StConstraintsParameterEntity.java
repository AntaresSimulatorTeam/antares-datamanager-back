package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity(name = "StConstraintsParameter")
@Table(name = "st_constraints_parameters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StConstraintsParameterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "st_constraints_parameters_seq_gen")
    @SequenceGenerator(name = "st_constraints_parameters_seq_gen", sequenceName = "st_constraints_parameters_sequence", allocationSize = 1)
    private Long id;

    @Column(name = "name", length = 40)
    private String name;

    @Column(name = "zone", length = 40)
    private String zone;

    @Column(name = "cluster", length = 40)
    private String cluster;

    @Column(name = "variable", length = 40)
    private String variable;

    @Column(name = "operator", length = 40)
    private String operator;

    @Column(name = "enabled")
    private Boolean enabled;

    @OneToMany(mappedBy = "parameter", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StConstraintsHoursEntity> hours;

    @ManyToOne(optional = false)
    @JoinColumn(name = "storage_id", nullable = false)
    private StStorageEntity storage;

}