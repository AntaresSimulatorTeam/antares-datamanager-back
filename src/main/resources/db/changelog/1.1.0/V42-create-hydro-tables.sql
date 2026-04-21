-- liquibase formatted sql

-- changeset metienne:110V042-1
CREATE TABLE hydro_series
(
    id                INTEGER,
    ts_name           VARCHAR(60) NOT NULL UNIQUE,
    type              VARCHAR(20),
    trajectory_id     INTEGER,
    PRIMARY KEY (id)
);

ALTER TABLE hydro_series
    ADD CONSTRAINT "hydro_series_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

CREATE SEQUENCE hydro_series_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- changeset metienne:110V042-2
CREATE TABLE hydro_allocation
(
    id                INTEGER,
    hydro             VARCHAR(20),
    load              VARCHAR(20),
    allocation        INTEGER,
    trajectory_id     INTEGER,
    PRIMARY KEY (id)
);

ALTER TABLE hydro_allocation
    ADD CONSTRAINT "hydro_allocation_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

CREATE SEQUENCE hydro_allocation_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- changeset metienne:110V042-3
CREATE TABLE hydro_parameters
(
    id                         INTEGER,
    node                       VARCHAR(20),
    inter_daily_breakdown      INTEGER,
    inter_daily_modulation     INTEGER,
    inter_monthly_breakdown    INTEGER,
    initialize_reservoir_date  INTEGER,
    pumping_efficiency         INTEGER,
    reservoir                  boolean,
    reservoir_capacity         numeric,
    follow_load                boolean,
    use_water                  boolean,
    trajectory_id              INTEGER,
    PRIMARY KEY (id)
);

ALTER TABLE hydro_parameters
    ADD CONSTRAINT "hydro_parameters_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

CREATE SEQUENCE hydro_parameters_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


