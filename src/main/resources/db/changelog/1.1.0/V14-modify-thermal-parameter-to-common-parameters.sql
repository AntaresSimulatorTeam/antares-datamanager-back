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