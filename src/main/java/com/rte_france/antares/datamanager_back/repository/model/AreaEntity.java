package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "Area")
@Table(name = "area")
public class AreaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "area_seq_gen")
    @SequenceGenerator(name = "area_seq_gen", sequenceName = "area_sequence", allocationSize = 1)
    private Integer id;

    private String name;

    private Double x;

    private Double y;

    private Double r;

    private Double g;

    private Double b;

}
