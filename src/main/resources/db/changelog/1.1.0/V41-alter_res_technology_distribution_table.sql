-- liquibase formatted sql
<<<<<<< Updated upstream
-- changeset vargas:110V41-1
ALTER TABLE res_technology_distribution ALTER COLUMN pecd_technology TYPE VARCHAR(40);

=======
-- changeset vargas:110V40-1

CREATE TABLE st_constraints_parameters
(
    id              INTEGER,
    storage_id      INTEGER NOT NULL,
    name            VARCHAR(40),
    zone            VARCHAR(10),
    cluster         VARCHAR(40),
    variable        VARCHAR(20),
    operator        VARCHAR(20),
    enabled         BOOLEAN,
    PRIMARY KEY (id)
    );

CREATE TABLE st_constraints_hours (
     id            INTEGER,
     parameter_id  INTEGER NOT NULL,
     occurrence    INTEGER,
     start_hour    INTEGER,
     end_hour      INTEGER,
     PRIMARY KEY (id)
);


ALTER TABLE st_constraints_parameters ADD CONSTRAINT "fk_parameters_storage" FOREIGN KEY (storage_id) REFERENCES st_storage (id);

ALTER TABLE st_constraints_hours ADD CONSTRAINT "fk_hours_parameters" FOREIGN KEY (parameter_id) REFERENCES st_constraints_parameters (id);

CREATE
    SEQUENCE st_constraints_parameters_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE
    SEQUENCE st_constraints_hours_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
>>>>>>> Stashed changes
