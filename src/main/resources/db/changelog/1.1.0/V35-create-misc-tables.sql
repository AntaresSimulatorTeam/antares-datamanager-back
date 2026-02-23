-- liquibase formatted sql

-- changeset elazaarmou:110V035-1
CREATE TABLE misc_cluster_capacity
(
    id                INTEGER,
    to_use            BOOLEAN DEFAULT false,
    area              VARCHAR(20),
    groupe            VARCHAR(40),
    cluster           VARCHAR(40),
    category          VARCHAR(20) DEFAULT 'power',
    capacity_by_year  numeric,
    trajectory_id     INTEGER,
    PRIMARY KEY (id)
);

ALTER TABLE misc_cluster_capacity
    ADD CONSTRAINT "misc_cluster_capacity_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

CREATE SEQUENCE misc_cluster_capacity_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- changeset elazaarmou:110V035-2
CREATE TABLE misc_load_factory
(
    id         INTEGER,
    ts_name VARCHAR(60) NOT NULL UNIQUE,
    checksum VARCHAR(255) NOT NULL,
    trajectory_id     INTEGER,
    PRIMARY KEY (id)
);

ALTER TABLE misc_load_factory
    ADD CONSTRAINT "misc_load_factory_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

CREATE SEQUENCE misc_load_factory_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


