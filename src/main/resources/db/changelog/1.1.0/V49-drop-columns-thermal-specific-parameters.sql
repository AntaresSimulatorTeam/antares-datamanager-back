-- liquibase formatted sql
-- changeset salemsd:110V49-1

ALTER TABLE thermal_specific_parameters DROP COLUMN node_entsoe;
ALTER TABLE thermal_specific_parameters DROP COLUMN comments;
