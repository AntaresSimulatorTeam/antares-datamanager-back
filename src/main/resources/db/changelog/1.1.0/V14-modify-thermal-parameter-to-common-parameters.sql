-- liquibase formatted sql
-- changeset vargas_tat:110V12-1
-- 1) rename table thermal_parameter -> thermal_common_parameters
ALTER TABLE IF EXISTS thermal_parameter RENAME TO thermal_common_parameters;

-- 2) rename column node -> cluster
ALTER TABLE thermal_common_parameters RENAME COLUMN node TO cluster;

-- 3) rename column node_ENTSOE -> cluster_PEMMDB
ALTER TABLE thermal_common_parameters RENAME COLUMN node_ENTSOE TO cluster_PEMMDB;

-- 4) drop efficiency column if exists
ALTER TABLE thermal_common_parameters DROP COLUMN efficiency;

-- 5) drop cluster column if exists
ALTER TABLE thermal_common_parameters DROP COLUMN cluster;

-- 6) drop cluster_pemmdb column if exists
ALTER TABLE thermal_common_parameters DROP COLUMN cluster_pemmdb;

-- 7) add thermal_cluster_ref_id column
ALTER TABLE thermal_common_parameters ADD COLUMN thermal_cluster_ref_id INTEGER;

-- 8) add constraint for thermal_cluster_ref table
ALTER TABLE thermal_common_parameters
    ADD CONSTRAINT "thermal_common_parameters_fk" FOREIGN KEY (thermal_cluster_ref_id) REFERENCES thermal_cluster_ref (id);