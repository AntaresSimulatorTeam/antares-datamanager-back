package com.rte_france.antares.datamanager_back.repository.model;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "thermal_economic_co2")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ThermalEconomicCo2Entity extends ThermalBaseEntity {
    @Id
    @SequenceGenerator(name = "thermal_economic_co2_seq", sequenceName = "thermal_economic_co2_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "thermal_economic_co2_seq")
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "trajectory_id", nullable = false)
    private TrajectoryEntity trajectory;

    @Column(length = 100, nullable = false)
    private String fuel;

    @Column(length = 100)
    private String country;

    @Column(name = "unit_co2", length = 50)
    private String unitCo2;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "co2_emission_year", nullable = false)
    private Integer year;

    @Column(name = "co2_emission_fuel", precision = 18, scale = 6, nullable = false)
    private BigDecimal co2EmissionFuel;

}
