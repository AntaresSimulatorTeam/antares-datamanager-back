-- liquibase formatted sql
-- changeset metienne:110V056-1

CREATE TABLE settings_general_parameters
(
    id                         INTEGER,
    mode                       VARCHAR(10),
    first_day                  INTEGER,
    last_day                   INTEGER,
    horizon                    VARCHAR(10),
    first_month                VARCHAR(10),
    first_week_day             VARCHAR(10),
    first_january              VARCHAR(10),
    leap_year                  boolean,
    nb_years                   INTEGER,
    building_mode              VARCHAR(10),
    selection_mode             boolean,
    year_by_year               boolean,
    simulation_synthesis       boolean,
    mc_scenario                boolean, 
    thematic_trimming          boolean,
    geographic_trimming        boolean,
    nb_timeseries_thermal      INTEGER,
    trajectory_id              INTEGER,
    PRIMARY KEY (id)
);

ALTER TABLE settings_general_parameters
    ADD CONSTRAINT "settings_general_parameters_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

CREATE SEQUENCE settings_general_parameters_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE settings_optimization_parameters
(
    id                                  INTEGER,
    binding_constraints                 boolean,
    hurdle_costs                        boolean,
    transmission_capacities             VARCHAR(40),
    thermal_clusters_min_stable_power   boolean,
    thermal_clusters_min_ud_time        boolean,
    day_ahead_reserve                   boolean,
    primary_reserve                     boolean,
    strategic_reserve                   boolean,
    spinning_reserve                    boolean,
    export_mps                          VARCHAR(20),
    unfeasible_problem_behavior         VARCHAR(20),
    simplex_optimization_range          VARCHAR(10),
    trajectory_id                       INTEGER,
    PRIMARY KEY (id)
);

ALTER TABLE settings_optimization_parameters
    ADD CONSTRAINT "settings_optimization_parameters_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

CREATE SEQUENCE settings_optimization_parameters_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE settings_advanced_parameters
(
    id                                  INTEGER,
    accuracy_on_correlation             VARCHAR(10),
    initial_reservoir_levels            VARCHAR(10),
    power_fluctuations                  VARCHAR(40),
    shedding_policy                     VARCHAR(40),
    hydro_pricing_mode                  VARCHAR(40),
    hydro_heuristic_policy              VARCHAR(10),
    unit_commitment_mode                VARCHAR(10),
    number_of_cores_mode                VARCHAR(10),
    day_ahead_reserve_management        VARCHAR(10),
    renewable_generation_modelling      VARCHAR(20),
    trajectory_id                       INTEGER,
    PRIMARY KEY (id)
);

ALTER TABLE settings_advanced_parameters
    ADD CONSTRAINT "settings_advanced_parameters_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

CREATE SEQUENCE settings_advanced_parameters_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE settings_seeds_parameters
(
    id                                  INTEGER,
    seed_tsgen_wind                     INTEGER,
    seed_tsgen_load                     INTEGER,
    seed_tsgen_hydro                    INTEGER,
    seed_tsgen_thermal                  INTEGER,
    seed_tsgen_solar                    INTEGER,
    seed_tsnumbers                      INTEGER,
    seed_unsupplied_energy_costs        INTEGER,
    seed_spilled_energy_costs           INTEGER,
    seed_thermal_costs                  INTEGER,
    seed_hydro_costs                    INTEGER,
    seed_initial_reservoir_levels       INTEGER,
    trajectory_id                       INTEGER,
    PRIMARY KEY (id)
);

ALTER TABLE settings_seeds_parameters
    ADD CONSTRAINT "settings_seeds_parameters_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

CREATE SEQUENCE settings_seeds_parameters_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;