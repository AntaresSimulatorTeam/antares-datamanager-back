package com.rte_france.antares.datamanager_back.repository.model;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "thermal_costs_rate")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThermalCostsRateEntity extends ThermalBaseEntity {
    @Id
    @SequenceGenerator(name = "thermal_costs_rate_seq", sequenceName = "thermal_costs_rate_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "thermal_costs_rate_seq")
    private Integer id;

    @Column(name = "rate_type", length = 50)
    private String rateType;

    @Column(name = "rate_year", nullable = false)
    private Integer year;

    @Column(name = "rate_value", precision = 18, scale = 6, nullable = false)
    private BigDecimal value;
}
