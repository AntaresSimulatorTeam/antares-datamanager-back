package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "AreaConfig")
@Table(name = "area_config")
public class AreaConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "area_config_seq_gen")
    @SequenceGenerator(name = "area_config_seq_gen", sequenceName = "area_config_sequence", allocationSize = 1)
    private Integer id;

    private Boolean powerToGas;

    private Boolean shortTermStorage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_id")
    private AreaEntity area;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trajectory_id")
    private TrajectoryEntity trajectory;


    public AreaConfigEntity(Boolean powerToGas, Boolean shortTermStorage, AreaEntity areaEntity) {
        this.powerToGas = powerToGas;
        this.shortTermStorage = shortTermStorage;
        this.area = areaEntity;
    }


}
