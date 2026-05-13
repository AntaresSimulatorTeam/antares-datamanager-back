package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.*;

@BatchSize(size = 1000)
@Getter
@Setter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "Trajectory")
@Table(name = "trajectory")
public class TrajectoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "trajectory_seq_gen")
    @SequenceGenerator(name = "trajectory_seq_gen", sequenceName = "trajectory_sequence", allocationSize = 1)
    private Integer id;

    private String fileName;

    private Long fileSize;

    private String checksum;

    private String type;

    private int version;

    private String createdBy;

    private LocalDateTime creationDate;

    private LocalDateTime lastModificationContentDate;

    private String horizon;

    @Column(name = "area")
    private String area;

    private String technology;

    private Boolean hasTimeSeries;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "trajectory", cascade = {CascadeType.ALL})
    List<AreaConfigEntity> areaConfigEntities;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "trajectory", cascade = {CascadeType.ALL})
    List<LinkEntity> linkEntities;

    @BatchSize(size = 10000)
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "trajectory", cascade = {CascadeType.ALL})
    List<ThermalClusterCapacityEntity> thermalClusterCapacities;

    @BatchSize(size = 10000)
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "trajectory", cascade = {CascadeType.ALL})
    List<ThermalCommonParameterEntity> thermalCommonParameters;

    @BatchSize(size = 10000)
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "trajectory", cascade = {CascadeType.ALL})
    List<ThermalSpecificParametersEntity>  thermalSpecificParameters;

    @BatchSize(size = 10000)
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "trajectory", cascade = {CascadeType.ALL})
    List<ThermalModulationParameterEntity> thermalModulationParameters;

    @BatchSize(size = 10000)
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "trajectory", cascade = {CascadeType.ALL})
    List<ThermalCostEntity> thermalCosts;

    @BatchSize(size = 10000)
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "trajectory", cascade = {CascadeType.ALL})
    List<ThermalCostsRateEntity> thermalCostsRates;

    @BatchSize(size = 10000)
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "trajectory", cascade = {CascadeType.ALL})
    List<ThermalEconomicCo2Entity> thermalEconomicCo2s;

    @BatchSize(size = 10000)
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "trajectory", cascade = {CascadeType.ALL})
    List<ThermalEconomicEnerContentEntity> thermalEconomicEnerContents;

    @BatchSize(size = 10000)
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "trajectory", cascade = {CascadeType.ALL})
    List<StStorageEntity> stStorageEntities;

    @BatchSize(size = 10000)
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "trajectory", cascade = {CascadeType.ALL})
    List<DsrClusterEntity> dsrClusterEntities;

    @BatchSize(size = 10000)
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "trajectory", cascade = {CascadeType.ALL})
    List<DsrCapacityModulationEntity> dsrCapacityModulationEntities;

    // Ajout des relations pour MISC
    @BatchSize(size = 10000)
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "trajectory", cascade = {CascadeType.ALL})
    List<MiscClusterCapacityEntity> miscClusterCapacityEntities;

    @BatchSize(size = 10000)
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "trajectory", cascade = {CascadeType.ALL})
    List<MiscLoadFactoryEntity> miscLoadFactoryEntities;

    // Ajout des relations pour RES
    @BatchSize(size = 10000)
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "trajectory", cascade = {CascadeType.ALL})
    List<ResClusterCapacityEntity> resClusterCapacityEntities;

    @BatchSize(size = 10000)
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "trajectory", cascade = {CascadeType.ALL})
    List<ResTechnologyDistributionEntity> resTechnologyDistributionCapacityEntities;

    @BatchSize(size = 10000)
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "trajectory", cascade = {CascadeType.ALL})
    List<ResZonalDistributionEntity> resZonalDistributionCapacityEntities;

    @BatchSize(size = 10000)
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "trajectory", cascade = {CascadeType.ALL})
    List<HydroSeriesEntity> hydroSeriesEntities;

    @BatchSize(size = 10000)
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "trajectory", cascade = {CascadeType.ALL})
    List<HydroAllocationEntity> hydroAllocationEntities;

    @BatchSize(size = 10000)
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "trajectory", cascade = {CascadeType.ALL})
    List<HydroParametersEntity> hydroParametersEntities;

    @ManyToMany
    @JoinTable(name = "scenario_trajectory",
            joinColumns = @JoinColumn(name = "trajectory_id"),
            inverseJoinColumns = @JoinColumn(name = "scenario_id"))
    @Builder.Default
    private Set<StudyEntity> scenarioEntities = new LinkedHashSet<>();

    @ManyToMany(cascade = CascadeType.PERSIST) // prevents transient by also persistign the new loads
    @JoinTable(name = "trajectory_load",
            joinColumns = @JoinColumn(name = "id_trajectory"),
            inverseJoinColumns = @JoinColumn(name = "id_load"))
    @Builder.Default
    private Set<LoadEntity> loadEntities = new LinkedHashSet<>();

    public void addLoadEntity(LoadEntity load) {
        Objects.requireNonNull(load);
        if (loadEntities.add(load)) {
            load.getTrajectoryEntities().add(this);
        }
    }

    @OneToMany(mappedBy = "trajectory", cascade = {CascadeType.MERGE, CascadeType.PERSIST}, orphanRemoval = true)
    private Set<WarningMessageEntity> warningMessages;

}
