-- liquibase formatted sql
-- changeset salemsd:110V055-1

ALTER TABLE hydro_parameters ALTER COLUMN inter_daily_breakdown     TYPE NUMERIC;
ALTER TABLE hydro_parameters ALTER COLUMN inter_daily_modulation    TYPE NUMERIC;
ALTER TABLE hydro_parameters ALTER COLUMN inter_monthly_breakdown   TYPE NUMERIC;
ALTER TABLE hydro_parameters ALTER COLUMN pumping_efficiency        TYPE NUMERIC;