-- liquibase formatted sql
-- changeset elazaarmou:110V76-1
CREATE TABLE hydro_parameters_me (
    id BIGSERIAL PRIMARY KEY,
    trajectory_id BIGINT NOT NULL,
    node VARCHAR(60) NOT NULL,
    inter_monthly_correlation NUMERIC,
    inter_daily_breakdown NUMERIC,
    intra_daily_modulation NUMERIC,
    inter_monthly_breakdown NUMERIC,
    initialize_reservoir_date INTEGER,
    leeway_low NUMERIC,
    leeway_up NUMERIC,
    pumping_efficiency NUMERIC,
    reservoir_management BOOLEAN,
    follow_load BOOLEAN,
    use_heuristic BOOLEAN,
    use_water BOOLEAN,
    hard_bounds BOOLEAN,
    use_leeway BOOLEAN,
    power_to_level BOOLEAN,
    CONSTRAINT fk_hydro_param_trajectory FOREIGN KEY (trajectory_id) REFERENCES trajectory(id) ON DELETE CASCADE
);

CREATE INDEX idx_hydro_param_trajectory ON hydro_parameters_me(trajectory_id);
CREATE INDEX idx_hydro_param_node ON hydro_parameters_me(node);
