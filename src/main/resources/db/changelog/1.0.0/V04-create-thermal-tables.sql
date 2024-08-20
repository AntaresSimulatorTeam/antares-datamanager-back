-- liquibase formatted sql
-- changeset elazaarmou:100V3-1
CREATE TABLE thermal_cluster_capacity
(
    id               INTEGER,
    scenario         VARCHAR(255),
    default_scenario BOOLEAN DEFAULT false,
    name             VARCHAR(255),
    category         VARCHAR(255),
    month_year       VARCHAR(255),
    capacity         numeric,
    to_use           BOOLEAN DEFAULT false,
    trajectory_id    INTEGER,
    PRIMARY KEY (id)
);
-- changeset elazaarmou:100V2-3
ALTER TABLE thermal_cluster_capacity
    ADD CONSTRAINT "thermal_cluster_capacity_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

CREATE SEQUENCE thermal_cluster_capacity_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- changeset elazaarmou:100V3-3
CREATE TABLE thermal_parameter
(
    id                            INTEGER,
    year_parameter                numeric,
    node                          VARCHAR(8),
    node_ENTSOE                   VARCHAR(8),
    comments                      VARCHAR(255),
    category                      VARCHAR(255),
    fuel                          VARCHAR(255),
    techno                        VARCHAR(255),
    cluster_PEMMDB                VARCHAR(255),
    cluster                       VARCHAR(255),
    enabled                       BOOLEAN DEFAULT false,
    efficiency_range               VARCHAR(255),
    efficiency_default            numeric,
    CO2                           numeric,
    OM_cost                       numeric,
    min_up_time                   numeric,
    min_down_time                 numeric,
    start_up_fuel                 numeric,
    start_up_fix_cost             numeric,
    start_up_fuel_cold_start      numeric,
    start_up_fix_cost_cold_start  numeric,
    start_up_fuel_hot_start       numeric,
    start_up_fix_cost_hot_start   numeric,
    transition_hot_warm           numeric,
    transition_hot_cold           numeric,
    shutdown_time                 numeric,
    startup_time                  numeric,
    FO_rate_default               numeric,
    FO_duration_default           numeric,
    PO_duration_default           numeric,
    PO_winter_default             numeric,
    min_stable_generation_default numeric,
    ramp_up                       numeric,
    ramp_down                     numeric,
    fixed_generation_reduction    numeric,
    min_stable_generation         numeric,
    spinning                      numeric,
    efficiency                    numeric,
    FO_rate                       numeric,
    FO_duration                   numeric,
    PO_duration                   numeric,
    PO_winter                     numeric,
    F1                            numeric,
    F2                            numeric,
    F3                            numeric,
    F4                            numeric,
    F5                            numeric,
    F6                            numeric,
    F7                            numeric,
    F8                            numeric,
    F9                            numeric,
    F10                           numeric,
    F11                           numeric,
    F12                           numeric,
    P1                            numeric,
    P2                            numeric,
    P3                            numeric,
    P4                            numeric,
    P5                            numeric,
    P6                            numeric,
    P7                            numeric,
    P8                            numeric,
    P9                            numeric,
    P10                           numeric,
    P11                           numeric,
    P12                           numeric,
    spread                        numeric,
    marginal_cost                 numeric,
    market_bid                    numeric,
    fixed_cost                    numeric,
    offset_variable_cost          numeric,
    NPO_max_winter                numeric,
    NPO_max_summer                numeric,
    nb_units                      numeric,
    MR_specific                   numeric,
    M1                            numeric,
    M2                            numeric,
    M3                            numeric,
    M4                            numeric,
    M5                            numeric,
    M6                            numeric,
    M7                            numeric,
    M8                            numeric,
    M9                            numeric,
    M10                           numeric,
    M11                           numeric,
    M12                           numeric,
    CM_specific                   numeric,
    C1                            numeric,
    C2                            numeric,
    C3                            numeric,
    C4                            numeric,
    C5                            numeric,
    C6                            numeric,
    C7                            numeric,
    C8                            numeric,
    C9                            numeric,
    C10                           numeric,
    C11                           numeric,
    C12                           numeric,
    trajectory_id    INTEGER,
    PRIMARY KEY (id)
);
-- changeset elazaarmou:100V6-3
ALTER TABLE thermal_parameter
    ADD CONSTRAINT "thermal_parameter_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

CREATE SEQUENCE thermal_parameter_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
-- changeset elazaarmou:100V3-4
CREATE TABLE thermal_cost_type
(
    id            INTEGER,
    country       VARCHAR(255),
    fuel          VARCHAR(255),
    scenario      VARCHAR(255),
    comment       VARCHAR(255),
    unit          VARCHAR(255),
    modulation    VARCHAR(255),
    ratio_NCV_HCV numeric,
    PRIMARY KEY (id)
);
-- changeset elazaarmou:100V3-5
CREATE TABLE thermal_cost
(
    id              INTEGER,
    cost            numeric,
    cost_year       numeric,
    thermal_type_id INTEGER,
    trajectory_id    INTEGER,
    PRIMARY KEY (id)
);
-- changeset elazaarmou:100V3-6
ALTER TABLE thermal_cost
    ADD CONSTRAINT "thermal_cost_year_FK1" FOREIGN KEY (thermal_type_id) REFERENCES thermal_cost_type (id);

ALTER TABLE thermal_cost
    ADD CONSTRAINT "thermal_cost_year_FK2" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

CREATE SEQUENCE thermal_cost_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE thermal_cost_type_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;