package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "thermal_modulation_parameters")
public class ThermalModulationParameterEntity extends ThermalBaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "thermal_modulation_parameters_seq")
    @SequenceGenerator(name = "thermal_modulation_parameters_seq", sequenceName = "thermal_modulation_parameters_sequence", allocationSize = 1)
    private Integer id;

    @Column(name = "ts_name")
    private String tsName;

    @Column(name = "checksum")
    private String checksum;

    @ManyToMany(mappedBy = "thermalModulationParams")
    @Builder.Default
    private Set<TrajectoryEntity> trajectoryEntities = new LinkedHashSet<>();

    public void addTrajectoryEntity(TrajectoryEntity trajectory) {
        Objects.requireNonNull(trajectory);
        if (trajectoryEntities.add(trajectory)) {
            trajectory.getThermalModulationParams().add(this);
        }
    }

}