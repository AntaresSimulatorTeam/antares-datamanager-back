package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "scenario")
public class StudyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private ProjectEntity project ;

    @Enumerated(EnumType.STRING)
    private StudyStatus status;

    private String horizon;

    @ManyToMany(mappedBy = "scenarioEntities")
    private Set<TrajectoryEntity> trajectories = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "scenario_tags", joinColumns = @JoinColumn(name = "scenario_id"))
    @Column(name = "tag")
    private List<String> tags;

    //une etude  = un horizon  ex (2030:2031)
}