-- liquibase formatted sql

-- changeset metienne:V68-create-p2g-table
CREATE TABLE p2g_capacity
(
    id                    INTEGER PRIMARY KEY,
    area                  VARCHAR(10),
    base_fatal_band       INTEGER,
    base_eff              INTEGER,
    base_capacity         INTEGER,
    marg_capacity         INTEGER,
    methanation_capacity  INTEGER,
    asservi_capacity      INTEGER,
    trajectory_id  INTEGER NOT NULL,
    CONSTRAINT "p2g_capacity_fk1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id)
);

CREATE SEQUENCE p2g_capacity_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE p2g_parameters
(
    id                          INTEGER PRIMARY KEY,
    fc_electrolyseur            INTEGER,
    facteur_surdimension_enr    INTEGER,
    part_pv_mix                 INTEGER,
    trajectory_id               INTEGER NOT NULL,
    CONSTRAINT "p2g_parameters_fk1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id)
);

CREATE SEQUENCE p2g_parameters_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE p2g_costs
(
    id                          INTEGER PRIMARY KEY,
    type                        VARCHAR(20) NOT NULL CHECK (type IN ('BASE', 'MARGINAL', 'METHANATION', 'ASSERVI')),
    modulation                  VARCHAR(10),
    cost                        INTEGER,
    trajectory_id               INTEGER NOT NULL,
    CONSTRAINT "p2g_costs_fk1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id)
);

CREATE SEQUENCE p2g_costs_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE p2g_market_modulation
(
    id                          INTEGER PRIMARY KEY,
    name                        VARCHAR(40),
    trajectory_id               INTEGER NOT NULL,
    CONSTRAINT "p2g_market_modulation_fk1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id)
);

CREATE SEQUENCE p2g_market_modulation_seq START WITH 1 INCREMENT BY 1;


