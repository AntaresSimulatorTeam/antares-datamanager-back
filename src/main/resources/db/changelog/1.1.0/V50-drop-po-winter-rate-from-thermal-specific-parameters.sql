-- liquibase formatted sql
-- changeset salemsd:110V50-1

ALTER TABLE thermal_specific_parameters DROP COLUMN IF EXISTS po_winter_rate;
