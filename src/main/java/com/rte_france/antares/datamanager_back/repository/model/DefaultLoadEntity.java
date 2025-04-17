package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "default_load")
public class DefaultLoadEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "load_default_seq_gen")
    @SequenceGenerator(name = "default_load_seq_gen", sequenceName = "load_default_sequence", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Integer id;

    private String name;

    @Column(name = "is_default")
    private Boolean isDefault;

    private String entity;
}