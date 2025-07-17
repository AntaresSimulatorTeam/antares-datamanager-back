-- liquibase formatted sql
-- changeset elazaarmou:110V08-1

--ALTER TABLE thermal_cluster_capacity DROP COLUMN default_scenario;
--ALTER TABLE thermal_cluster_capacity DROP COLUMN scenario;
ALTER TABLE thermal_cluster_capacity ADD COLUMN type VARCHAR(50);
ALTER TABLE thermal_cluster_capacity ADD COLUMN area VARCHAR(50);