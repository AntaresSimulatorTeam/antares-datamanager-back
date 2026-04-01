package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

@Entity(name = "StConstraintsHours")
@Table(name = "st_constraints_hours")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StConstraintsHoursEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "st_constraints_hours_seq_gen")
    @SequenceGenerator(name = "st_constraints_hours_seq_gen", sequenceName = "st_constraints_hours_sequence", allocationSize = 1)
    private Long id;

    @Column(name = "occurrence")
    private Integer occurrence;

    @Column(name = "start_hour")
    private Integer startHour;

    @Column(name = "end_hour")
    private Integer endHour;

    @ManyToOne(optional = false)
    @JoinColumn(name = "parameter_id", nullable = false)
    private StConstraintsParameterEntity parameter;

}
