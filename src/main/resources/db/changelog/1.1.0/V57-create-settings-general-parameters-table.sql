-- liquibase formatted sql
-- changeset metienne:110V056-1

CREATE TABLE settings_general_parameters
(
    id                         INTEGER,
    mode                       VARCHAR(10),
    horizon                    VARCHAR(10),
    nb_years                   INTEGER,
    simulation_start           INTEGER,
    simulation_end             INTEGER,
    january_first              VARCHAR(10),
    first_month_in_year        VARCHAR(10),
    first_week_day             VARCHAR(10),
    leap_year                  boolean,
    year_by_year               boolean,
    simulation_synthesis       boolean,
    building_mode              VARCHAR(10),
    user_playlist              boolean,
    thematic_trimming          boolean,
    geographic_trimming        boolean,
    nb_timeseries_thermal      INTEGER,
    store_new_set              boolean, 
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
    simplex_range                       VARCHAR(10),
    transmission_capacities             VARCHAR(40),
    include_constraints                 boolean,
    include_hurdlecosts                 boolean,
    include_tc_minstablepower           boolean,
    include_tc_min_ud_time              boolean,
    include_dayahead                    boolean,
    include_strategicreserve            boolean,
    include_spinningreserve             boolean,
    include_primaryreserve              boolean,
    include_exportmps                   VARCHAR(20),
    include_unfeasible_problem_behavior VARCHAR(20),
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
    id                                              INTEGER,
    hydro_heuristic_policy                          VARCHAR(40),
    hydro_pricing_mode                              VARCHAR(10),
    power_fluctuations                              VARCHAR(40),
    shedding_policy                                 VARCHAR(40),
    unit_commitment_mode                            VARCHAR(10),
    number_of_cores_mode                            VARCHAR(10),
    renewable_generation_modelling                  VARCHAR(20),
    accuracy_on_correlation                         VARCHAR(20),
    accurate_shave_peaks_include_short_term_storage boolean,
    trajectory_id                                   INTEGER,
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
    seed_tsgen_thermal                  INTEGER,
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