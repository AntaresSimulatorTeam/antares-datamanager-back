package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Locale;

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

    public void setName(String name) {
        this.name = (name == null) ? null : name.toUpperCase(Locale.ROOT);
    }

    @PostLoad
    private void normalizeAfterLoad() {
        if (name != null) {
            name = name.toUpperCase(Locale.ROOT);
        }
    }

    @PrePersist
    @PreUpdate
    private void normalizeName() {
        if (name != null) {
            name = name.toUpperCase(Locale.ROOT);
        }
    }

}
