-- liquibase formatted sql

-- changeset metienne:V69-create-p2g-table
CREATE TABLE p2g_capacity
(
    id                    INTEGER PRIMARY KEY,
    area                  VARCHAR(10),
    base_fatal_band       NUMERIC,
    base_eff              NUMERIC,
    base_capacity         NUMERIC,
    marg_capacity         NUMERIC,
    methanation_capacity  NUMERIC,
    asservi_capacity      NUMERIC,
    trajectory_id  INTEGER NOT NULL,
    CONSTRAINT "p2g_capacity_fk1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id)
);

CREATE SEQUENCE p2g_capacity_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE p2g_parameters
(
    id                          INTEGER PRIMARY KEY,
    fc_electrolyseur            NUMERIC,
    facteur_surdimension_enr    NUMERIC,
    part_pv_mix                 NUMERIC,
    trajectory_id               INTEGER NOT NULL,
    CONSTRAINT "p2g_parameters_fk1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id)
);

CREATE SEQUENCE p2g_parameters_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE p2g_costs
(
    id                          INTEGER PRIMARY KEY,
    type                        VARCHAR(20),
    modulation                  VARCHAR(10),
    cost                        NUMERIC,
    trajectory_id               INTEGER NOT NULL,
    CONSTRAINT "p2g_costs_fk1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id)
);

CREATE SEQUENCE p2g_costs_seq START WITH 1 INCREMENT BY 1;


