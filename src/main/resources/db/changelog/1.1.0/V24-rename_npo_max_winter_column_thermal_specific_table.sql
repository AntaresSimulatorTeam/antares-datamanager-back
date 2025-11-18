-- liquibase formatted sql
-- changeset vargas_tat:110V24-1

ALTER TABLE thermal_specific_parameters RENAME COLUMN npo_max_winther TO npo_max_winter;
