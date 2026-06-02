-- liquibase formatted sql
-- changeset elazaarmou:110V048-1
-- Create nuclear_modulation_parameter table

CREATE SEQUENCE nuclear_modulation_parameter_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
CREATE TABLE IF NOT EXISTS nuclear_modulation_parameter (
    id INTEGER PRIMARY KEY ,
    trajectory_id INTEGER NOT NULL,
    type VARCHAR(50) NOT NULL CHECK (type IN ('nucFR_modul_hourly', 'nucFR_modul_daily', 'nucFR_modul_weekly')),
    "value" NUMERIC NOT NULL,
    FOREIGN KEY (trajectory_id) REFERENCES trajectory(id) ON DELETE CASCADE
);
