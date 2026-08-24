-- liquibase formatted sql

-- changeset vargas_tat:V67-rename-scenario-builder-data-to-modulo
ALTER TABLE scenario_builder RENAME COLUMN data TO modulo;
