package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.Set;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "load")
public class LoadEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "load_seq_gen")
    @SequenceGenerator(name = "load_seq_gen", sequenceName = "load_sequence", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "output_file_name")
    private String outPutFileName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trajectory_id")
    private TrajectoryEntity trajectory;

    @ManyToMany(mappedBy = "loadEntities")
    private Set<TrajectoryEntity> trajectoryEntities = new LinkedHashSet<>();

    @ManyToOne
    @JoinColumn(name = "study_id", nullable = false)
    private StudyEntity study;

}
