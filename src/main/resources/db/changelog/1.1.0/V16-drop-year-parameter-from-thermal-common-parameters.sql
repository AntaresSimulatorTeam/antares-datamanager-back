-- liquibase formatted sql
-- changeset vargas_tat:110V16-1

ALTER TABLE thermal_common_parameters DROP COLUMN year_parameter;
ALTER TABLE thermal_common_parameters DROP COLUMN type;
