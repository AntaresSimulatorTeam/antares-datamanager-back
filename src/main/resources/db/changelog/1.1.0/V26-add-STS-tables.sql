-- liquibase formatted sql
-- changeset elazaarmou:110V26-01

CREATE TABLE IF NOT EXISTS st_storage
(
    id            INTEGER NOT NULL,
    area       VARCHAR(10),
    name      VARCHAR(40),
    groupe     VARCHAR(40),
    injection    NUMERIC,
    withdrawal   NUMERIC,
    storage      NUMERIC,
    efficiency_injection NUMERIC,
    efficiency_withdrawal INTEGER,
    initial_level NUMERIC,
    initial_level_optim BOOLEAN,
    enabled      BOOLEAN,
    series       BOOLEAN,
    st_constraints  BOOLEAN,
    trajectory_id INTEGER,
    CONSTRAINT pk_st_storage PRIMARY KEY (id)
);

ALTER TABLE st_storage ADD CONSTRAINT "st_storage_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

CREATE SEQUENCE st_storage_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
