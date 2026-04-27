-- liquibase formatted sql
-- changeset metienne:110V043-1
ALTER TABLE hydro_series DROP CONSTRAINT IF EXISTS hydro_series_ts_name_key;


