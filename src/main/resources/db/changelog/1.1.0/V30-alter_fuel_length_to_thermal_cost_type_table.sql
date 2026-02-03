-- liquibase formatted sql
-- changeset etiennemar:110V030-1
ALTER TABLE thermal_cost_type ALTER COLUMN fuel TYPE VARCHAR(20);