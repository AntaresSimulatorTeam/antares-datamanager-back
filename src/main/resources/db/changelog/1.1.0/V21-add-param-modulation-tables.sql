-- liquibase formatted sql
-- changeset elazaarmou:110V21-01

CREATE TABLE IF NOT EXISTS thermal_modulation_parameters
(
    id            INTEGER NOT NULL,
    ts_name       VARCHAR(255),
    checksum      VARCHAR(255),
    trajectory_id INTEGER,
    CONSTRAINT pk_thermal_modulation_parameters PRIMARY KEY (id)
);

CREATE
SEQUENCE thermal_modulation_parameters_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER TABLE thermal_modulation_parameters
    ADD CONSTRAINT "thermal_modulation_parameters_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);