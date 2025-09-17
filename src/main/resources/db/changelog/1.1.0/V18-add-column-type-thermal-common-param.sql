-- liquibase formatted sql
-- changeset elazaarmou:110V18-1

ALTER TABLE thermal_common_parameters ADD COLUMN type VARCHAR(40);
