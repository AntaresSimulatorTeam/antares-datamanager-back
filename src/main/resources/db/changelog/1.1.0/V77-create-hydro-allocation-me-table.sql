-- liquibase formatted sql
-- changeset elazaarmou:110V77-1
CREATE TABLE hydro_allocation_me (
    id BIGSERIAL PRIMARY KEY,
    trajectory_id BIGINT NOT NULL,
    area VARCHAR(60) NOT NULL,
    node VARCHAR(60),
    allocation_coefficient NUMERIC,
    CONSTRAINT fk_hydro_alloc_trajectory FOREIGN KEY (trajectory_id) REFERENCES trajectory(id) ON DELETE CASCADE
);

CREATE INDEX idx_hydro_alloc_trajectory ON hydro_allocation_me(trajectory_id);
CREATE INDEX idx_hydro_alloc_area ON hydro_allocation_me(area);
