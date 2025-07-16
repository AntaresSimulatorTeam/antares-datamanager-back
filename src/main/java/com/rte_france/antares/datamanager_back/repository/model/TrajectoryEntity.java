package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
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

    @Column(name = "area")
    private String loadArea;

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
    @Builder.Default
    private Set<StudyEntity> scenarioEntities = new LinkedHashSet<>();

    public void addScenarioEntity(StudyEntity studyEntity) {
        Objects.requireNonNull(studyEntity);
        if (scenarioEntities.add(studyEntity)) {
            studyEntity.getTrajectories().add(this);
        }
    }

    public void removeScenarioEntity(StudyEntity studyEntity) {
        Objects.requireNonNull(studyEntity);
        if (scenarioEntities.remove(studyEntity)) {
            studyEntity.getTrajectories().remove(this);
        }
    }


    @ManyToMany(cascade = CascadeType.PERSIST) // prevents transient by also persistign the new loads
    @JoinTable(name = "trajectory_load",
            joinColumns = @JoinColumn(name = "id_trajectory"),
            inverseJoinColumns = @JoinColumn(name = "id_load"))
    @Builder.Default
    private Set<LoadEntity> loadEntities = new LinkedHashSet<>();

    public void addLoadEntity(LoadEntity load) {
        Objects.requireNonNull(load);
        if (loadEntities.add(load)) {
            load.getTrajectoryEntities().add(this);
        }
    }

    public void removeLoadEntity(LoadEntity load) {
        Objects.requireNonNull(load);
        if (loadEntities.remove(load)) {
            load.getTrajectoryEntities().remove(this);
        }
    }

    @OneToMany(mappedBy = "trajectory", cascade = {CascadeType.MERGE, CascadeType.PERSIST}, orphanRemoval = true)
    private Set<WarningMessageEntity> warningMessages;
}
