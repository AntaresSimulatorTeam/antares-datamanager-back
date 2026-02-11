-- liquibase formatted sql
-- changeset elazaarmou:110V032-1
CREATE TABLE dsr_cluster
(
    id                INTEGER,
    to_use            BOOLEAN DEFAULT false,
    area              VARCHAR(40),
    name              VARCHAR(40),
    capacity          numeric,
    reliability       numeric,
    nb_hour_per_day   INTEGER,
    max_hour_per_day  INTEGER,
    price             numeric,
    nb_units          INTEGER,
    fo_rate           numeric,
    fo_duration       INTEGER,
    modulation        BOOLEAN DEFAULT false,
    trajectory_id     INTEGER,
    PRIMARY KEY (id)
);

-- changeset elazaarmou:110V032-2
ALTER TABLE dsr_cluster
    ADD CONSTRAINT "dsr_cluster_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

-- changeset elazaarmou:110V032-3
CREATE SEQUENCE dsr_cluster_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- changeset elazaarmou:110V032-4
CREATE TABLE dsr_capacity_modulation
(
    id                INTEGER,
    trajectory_id     INTEGER,
    date_time         TIMESTAMP,
    area_cluster_name VARCHAR(40),
    capacity_value    numeric,
    PRIMARY KEY (id)
);

-- changeset elazaarmou:110V032-5
ALTER TABLE dsr_capacity_modulation
    ADD CONSTRAINT "dsr_capacity_modulation_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

-- changeset elazaarmou:110V032-6
CREATE SEQUENCE dsr_capacity_modulation_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- changeset elazaarmou:110V032-7
CREATE INDEX dsr_cluster_trajectory_idx ON dsr_cluster (trajectory_id);

-- changeset elazaarmou:110V032-8
CREATE INDEX dsr_cm_trajectory_area_cluster_idx ON dsr_capacity_modulation (trajectory_id, area_cluster_name);
