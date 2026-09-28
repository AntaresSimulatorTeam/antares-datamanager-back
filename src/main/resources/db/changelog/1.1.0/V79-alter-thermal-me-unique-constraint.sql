-- liquibase formatted sql

-- changeset metienne:110V79-1
-- 2. Rendre la colonne NOT NULL
ALTER TABLE thermal_me
    ALTER COLUMN cluster_name SET NOT NULL;
    
ALTER TABLE thermal_me
    DROP CONSTRAINT IF EXISTS thermal_me_uk_node_trajectory;

ALTER TABLE thermal_me
    ADD CONSTRAINT thermal_me_uk_node_cluster_trajectory
        UNIQUE (node, cluster_name, trajectory_id);
