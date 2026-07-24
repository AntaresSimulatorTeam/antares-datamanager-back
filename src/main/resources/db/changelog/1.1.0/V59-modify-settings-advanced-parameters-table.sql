-- liquibase formatted sql
-- changeset elazaarmou:110V059-1
ALTER TABLE settings_advanced_parameters DROP COLUMN accuracy_on_correlation;
ALTER TABLE settings_advanced_parameters DROP COLUMN initial_reservoir_levels;

