-- liquibase formatted sql
-- changeset elazaarmou:110V19-1

ALTER TABLE thermal_specific_parameters ADD COLUMN area VARCHAR(40);
