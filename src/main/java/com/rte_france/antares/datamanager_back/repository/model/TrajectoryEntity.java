package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
@BatchSize(size = 1000)
@Getter
@Setter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "Trajectory")
@Table(name = "trajectory")
public class TrajectoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "trajectory_seq_gen")
    @SequenceGenerator(name = "trajectory_seq_gen", sequenceName = "trajectory_sequence", allocationSize = 1)
    private Integer id;

    private String fileName;

    private Long fileSize;

    private String checksum;

    private String type;

    private int version;

    private String createdBy;

    private LocalDateTime creationDate;

    private LocalDateTime lastModificationContentDate;

    private String horizon;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "trajectory", cascade = {CascadeType.ALL})
    List<AreaConfigEntity> areaConfigEntities;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "trajectory", cascade = {CascadeType.ALL})
    List<LinkEntity> linkEntities;

    @BatchSize(size = 10000)
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "trajectory", cascade = {CascadeType.ALL})
    List<ThermalClusterCapacityEntity> thermalClusterCapacities;

    @BatchSize(size = 10000)
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "trajectory", cascade = {CascadeType.ALL})
    List<ThermalParameterEntity> thermalClusterParameters;

    @BatchSize(size = 10000)
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "trajectory", cascade = {CascadeType.ALL})
    List<ThermalCostEntity> thermalCostEntities;

    @ManyToMany
    @JoinTable(name = "scenario_trajectory",
            joinColumns = @JoinColumn(name = "trajectory_id"),
            inverseJoinColumns = @JoinColumn(name = "scenario_id"))
    private Set<StudyEntity> scenarioEntities = new LinkedHashSet<>();
}
