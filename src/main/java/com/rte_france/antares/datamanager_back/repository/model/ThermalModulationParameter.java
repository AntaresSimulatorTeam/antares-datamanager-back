package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "thermal_modulation_parameters")
public class ThermalModulationParameter extends ThermalBaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "thermal_modulation_parameters_seq")
    @SequenceGenerator(name = "thermal_modulation_parameters_seq", sequenceName = "thermal_modulation_parameters_sequence", allocationSize = 1)
    private Integer id;

    @Column(name = "ts_name")
    private String tsName;

    @Column(name = "checksum")
    private String checksum;

}