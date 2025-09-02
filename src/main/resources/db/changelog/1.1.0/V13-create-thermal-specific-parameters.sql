-- liquibase formatted sql
-- changeset vargas_tat:110V13-1
CREATE TABLE IF NOT EXISTS thermal_specific_parameters (
    id INTEGER NOT NULL,
    node VARCHAR(20),
    node_entsoe VARCHAR(20),
    comments VARCHAR(255),
    cluster_pemmdb VARCHAR(20),
    cluster VARCHAR(20),
    min_stable_generation NUMERIC,
    spinning NUMERIC,
    efficiency NUMERIC,
    fo_rate NUMERIC,
    fo_duration NUMERIC,
    po_duration NUMERIC,
    po_winter NUMERIC,
    marginal_cost NUMERIC,
    market_bid NUMERIC,
    mr_specific INTEGER,
    cm_specific INTEGER,
    npo_max_winther INTEGER,
    npo_max_summer INTEGER,
    nb_unit INTEGER,
    po_winter_rate NUMERIC,
    f1 NUMERIC,
    f2 NUMERIC,
    f3 NUMERIC,
    f4 NUMERIC,
    f5 NUMERIC,
    f6 NUMERIC,
    f7 NUMERIC,
    f8 NUMERIC,
    f9 NUMERIC,
    f10 NUMERIC,
    f11 NUMERIC,
    f12 NUMERIC,
    p1 INTEGER,
    p2 INTEGER,
    p3 INTEGER,
    p4 INTEGER,
    p5 INTEGER,
    p6 INTEGER,
    p7 INTEGER,
    p8 INTEGER,
    p9 INTEGER,
    p10 INTEGER,
    p11 INTEGER,
    p12 INTEGER,
    trajectory_id    INTEGER,
    CONSTRAINT pk_thermal_specific_parameters PRIMARY KEY (id)
    );

CREATE
    SEQUENCE thermal_specific_parameters_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER TABLE thermal_specific_parameters
    ADD CONSTRAINT "thermal_specific_parameters_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);