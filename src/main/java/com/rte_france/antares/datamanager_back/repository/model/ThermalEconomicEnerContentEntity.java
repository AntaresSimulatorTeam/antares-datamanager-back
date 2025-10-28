package com.rte_france.antares.datamanager_back.repository.model;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "thermal_economic_ener_content")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ThermalEconomicEnerContentEntity extends ThermalBaseEntity{
    @Id
    @SequenceGenerator(name = "thermal_economic_ener_content_seq", sequenceName = "thermal_economic_ener_content_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "thermal_economic_ener_content_seq")
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "trajectory_id", nullable = false)
    private TrajectoryEntity trajectory;

    @Column(name = "ener_value", precision = 18, scale = 6, nullable = false)
    private BigDecimal value;

    @Column(length = 50)
    private String unit;

    @Column(columnDefinition = "text")
    private String comment;
}