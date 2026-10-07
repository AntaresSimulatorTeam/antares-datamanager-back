-- liquibase formatted sql

-- changeset datamanager:110V80-1
ALTER TABLE nuclear_modulation_parameter
    DROP CONSTRAINT IF EXISTS nuclear_modulation_parameter_type_check;

ALTER TABLE nuclear_modulation_parameter
    ADD CONSTRAINT nuclear_modulation_parameter_type_check
        CHECK (type IN ('nucFR_modul_hourly', 'nucFR_modul_daily', 'nucFR_modul_min_weekly', 'nucFR_modul_max_weekly'));
