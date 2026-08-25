-- liquibase formatted sql

-- changeset vargas_tat:V67-rename-scenario-builder-data-to-modulo et add category
ALTER TABLE scenario_builder RENAME COLUMN data TO modulo;

ALTER TABLE scenario_builder
    ADD COLUMN category VARCHAR(100) NOT NULL;
