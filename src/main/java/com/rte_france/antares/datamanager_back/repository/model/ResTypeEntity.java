package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity(name = "ResType")
@Table(name = "res_type")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResTypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "res_type_seq_gen")
    @SequenceGenerator(name = "res_type_seq_gen", sequenceName = "res_type_sequence", allocationSize = 1)
    private Integer id;

    private String label;
}

