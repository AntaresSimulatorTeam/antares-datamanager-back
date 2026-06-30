package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
@Entity
@Table(name = "adequacy_patch_mode")
public class AdqpModeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "adqp_mode_seq_gen")
    @SequenceGenerator(name = "adqp_mode_seq_gen", sequenceName = "adqp_mode_sequence", allocationSize = 1)
    private Integer id;

    private String area;

    private String mode;
}
