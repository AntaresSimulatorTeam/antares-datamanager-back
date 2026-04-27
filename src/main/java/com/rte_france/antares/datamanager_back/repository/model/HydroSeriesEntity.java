package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

@Entity(name = "HydroSeries")
@Table(name = "hydro_series")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HydroSeriesEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hydro_series_seq_gen")
    @SequenceGenerator(name = "hydro_series_seq_gen", sequenceName = "hydro_series_sequence", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "trajectory_id", nullable = false)
    private TrajectoryEntity trajectory;

    @Column(name = "ts_name", length = 60, nullable = false)
    private String tsName;

    @Column(name = "type", length = 40)
    private String type;
}
