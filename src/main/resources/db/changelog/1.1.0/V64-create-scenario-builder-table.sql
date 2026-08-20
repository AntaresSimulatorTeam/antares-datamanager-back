-- liquibase formatted sql

-- changeset elazaarmou:V64-create-scenario-builder-table
CREATE TABLE scenario_builder
(
    id             INTEGER PRIMARY KEY,
    data           VARCHAR(100),
    trajectory_id  INTEGER NOT NULL,
    CONSTRAINT "scenario_builder_fk1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id)
);

-- changeset elazaarmou:V64-create-scenario-builder-sequence
CREATE SEQUENCE scenario_builder_seq START WITH 1 INCREMENT BY 1;
