package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.Hibernate;

import java.io.Serializable;
import java.util.Objects;

@Getter
@Setter
@Embeddable
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class PinnedProjectEntityId implements Serializable {
    private static final long serialVersionUID = -3813678937923547138L;
    @Size(max = 10)
    @NotNull
    @Column(name = "nni", nullable = false, length = 10)
    private String nni;

    @NotNull
    @Column(name = "project_id", nullable = false)
    private Integer projectId;


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        PinnedProjectEntityId entity = (PinnedProjectEntityId) o;
        return Objects.equals(this.nni, entity.nni) &&
                Objects.equals(this.projectId, entity.projectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nni, projectId);
    }

}