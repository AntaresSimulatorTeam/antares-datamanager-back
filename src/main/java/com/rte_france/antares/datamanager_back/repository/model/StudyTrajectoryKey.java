package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

@Getter
@Setter
@Embeddable
public class StudyTrajectoryKey implements Serializable {
    @Serial
    private static final long serialVersionUID = 2716259068521532043L;
    @NotNull
    @Column(name = "scenario_id", nullable = false)
    private Integer scenarioId;

    @NotNull
    @Column(name = "trajectory_id", nullable = false)
    private Integer trajectoryId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        StudyTrajectoryKey entity = (StudyTrajectoryKey) o;
        return Objects.equals(this.trajectoryId, entity.trajectoryId) &&
                Objects.equals(this.scenarioId, entity.scenarioId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(trajectoryId, scenarioId);
    }

}