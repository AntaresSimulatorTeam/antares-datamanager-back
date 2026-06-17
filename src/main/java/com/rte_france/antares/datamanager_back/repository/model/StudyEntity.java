package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.*;

@Getter
@Setter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "scenario")
public class StudyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "study_seq_gen")
    @SequenceGenerator(name = "study_seq_gen", sequenceName = "study_sequence", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Size(max = 255)
    @Column(name = "name")
    private String name;

    @Size(max = 255)
    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "creation_date")
    private LocalDateTime creationDate;

    @Column(name = "generation_date")
    private LocalDateTime generationDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private ProjectEntity project ;

    @Enumerated(EnumType.STRING)
    private StudyStatus status;

    private String horizon;

    @Column(name = "hvdc")
    private Boolean hvdc;

    @OneToMany(mappedBy = "studyEntity", orphanRemoval = true)
    private Set<StudyTrajectoryEntity> studyTrajectoryEntities = new LinkedHashSet<>();

    @ManyToMany(mappedBy = "scenarioEntities")
    @Builder.Default
    private Set<TrajectoryEntity> trajectories = new LinkedHashSet<>();

    public void addTrajectoryEntity(TrajectoryEntity trajectory) {
        Objects.requireNonNull(trajectory);
        if (trajectories.add(trajectory)) {
            trajectory.getScenarioEntities().add(this);
        }
    }

    public void removeTrajectoryEntity(TrajectoryEntity trajectory) {
        Objects.requireNonNull(trajectory);
        if (trajectories.remove(trajectory)) {
            trajectory.getScenarioEntities().remove(this);
        }
    }

    @ElementCollection
    @CollectionTable(name = "scenario_tags", joinColumns = @JoinColumn(name = "scenario_id"))
    @Column(name = "tag")
    private List<String> tags;

    @OneToMany(mappedBy = "study", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<WarningMessageEntity> warningMessages = new HashSet<>();

    //une etude  = un horizon  ex (2030:2031)
}