-- liquibase formatted sql
-- changeset elazaarmou:110V10-1

ALTER TABLE thermal_cluster_capacity DROP COLUMN name;
ALTER TABLE thermal_cluster_capacity DROP COLUMN default_scenario;
ALTER TABLE thermal_cluster_capacity DROP COLUMN scenario;
ALTER TABLE thermal_cluster_capacity ADD COLUMN type VARCHAR(50);
ALTER TABLE thermal_cluster_capacity ADD COLUMN area VARCHAR(50);

-- changeset elazaarmou:110V10-2
CREATE TABLE thermal_technology
(
    id            INTEGER,
    name          VARCHAR(40),
    PRIMARY KEY (id)
    );

CREATE TABLE thermal_cluster_ref (
     id            INTEGER,
     name          VARCHAR(40),
     name_pemmdb   VARCHAR(40),
     thermal_technology_id INTEGER,--type
     PRIMARY KEY (id)
);

ALTER TABLE thermal_cluster_ref
    ADD CONSTRAINT "thermal_cluster_ref_fk" FOREIGN KEY (thermal_technology_id) REFERENCES thermal_technology (id);

ALTER TABLE thermal_cluster_capacity ADD COLUMN thermal_cluster_ref_id INTEGER;

ALTER TABLE thermal_cluster_capacity
    ADD CONSTRAINT "thermal_cluster_capacity_fk" FOREIGN KEY (thermal_cluster_ref_id) REFERENCES thermal_cluster_ref (id);

ALTER TABLE thermal_cluster_capacity DROP COLUMN type;

INSERT INTO thermal_technology (id, name)
VALUES
    (1, 'CCGT'),
    (2, 'Coal'),
    (3, 'DSR'),
    (4, 'Nuclear'),
    (5, 'Puissance complémentaire'),
    (6, 'Storage battery'),
    (7, 'Storage EV'),
    (8, 'TBD');

-- changeset elazaarmou:110V10-3

CREATE
    SEQUENCE thermal_cluster_ref_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
