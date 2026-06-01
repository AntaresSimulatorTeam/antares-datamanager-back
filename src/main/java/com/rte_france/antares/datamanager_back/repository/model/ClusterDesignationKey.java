package com.rte_france.antares.datamanager_back.repository.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.Hibernate;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

@Builder
@Getter
@Setter
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class ClusterDesignationKey implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    
    @NotNull
    @Column(name = "cluster_id", nullable = false)
    private Integer clusterId;

    @NotNull
    @Column(name = "nom_cluster", nullable = false, length = 20)
    private String nomCluster;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        ClusterDesignationKey entity = (ClusterDesignationKey) o;
        return Objects.equals(this.clusterId, entity.clusterId) &&
                Objects.equals(this.nomCluster, entity.nomCluster);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clusterId, nomCluster);
    }
}

