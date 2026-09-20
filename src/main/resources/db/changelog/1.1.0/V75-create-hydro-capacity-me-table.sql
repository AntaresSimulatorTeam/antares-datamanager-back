-- liquibase formatted sql

-- changeset elazaarmou:110V75-1
CREATE TABLE hydro_capacity_me
(
    id                              INTEGER         NOT NULL,
    node                            VARCHAR(60)     NOT NULL,
    reservoir_capacity              NUMERIC,
    generating_pmax_timestep        VARCHAR(20),
    generating_pmax                 NUMERIC,
    hours_at_generating_pmax        NUMERIC,
    pumping_pmax_timestep           VARCHAR(20),
    pumping_pmax                    NUMERIC,
    hours_at_pumping_pmax           NUMERIC,
    trajectory_id                   INTEGER         NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT hydro_capacity_me_fk_trajectory
        FOREIGN KEY (trajectory_id) REFERENCES trajectory (id)
);

ALTER TABLE hydro_capacity_me
    ADD CONSTRAINT hydro_capacity_me_uk_node_trajectory
        UNIQUE (node, trajectory_id);

CREATE SEQUENCE hydro_capacity_me_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
