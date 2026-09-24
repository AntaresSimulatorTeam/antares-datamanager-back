-- liquibase formatted sql

-- changeset metienne:110V76-1
CREATE TABLE thermal_me
(
    id                              INTEGER         NOT NULL,
    node                            VARCHAR(60)     NOT NULL,
    group_name                      VARCHAR(20),
    cluster_name                    VARCHAR(60),
    enabled                         BOOLEAN,
    nominal_capacity                NUMERIC,
    nb_unit                         INTEGER,
    marginal_cost                   NUMERIC,
    marginal_cost_timestep          VARCHAR(10),
    marginal_cost_modulation        INTEGER,
    market_bid_cost                 NUMERIC,
    market_bid_cost_timestep        VARCHAR(10),
    market_bid_cost_modulation      INTEGER,
    mr_modulation                   INTEGER,
    mr_timestep                     VARCHAR(10),
    mr_activate                     BOOLEAN,
    cm_timestep                     VARCHAR(10),
    cm_modulation                   INTEGER,
    trajectory_id                   INTEGER         NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT thermal_me_fk_trajectory
        FOREIGN KEY (trajectory_id) REFERENCES trajectory (id)
);

ALTER TABLE thermal_me
    ADD CONSTRAINT thermal_me_uk_node_trajectory
        UNIQUE (node, trajectory_id);

CREATE SEQUENCE thermal_me_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
