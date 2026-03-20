-- liquibase formatted sql

-- changeset metienne:110V036-1
CREATE TABLE res_cluster_capacity
(
    id                INTEGER,
    to_use            BOOLEAN DEFAULT false,
    area              VARCHAR(20),
    groupe             VARCHAR(40),
    cluster           VARCHAR(40),
    pecd_zone         VARCHAR(10),
    category          VARCHAR(20) DEFAULT 'power',
    capacity_by_year  numeric,
    trajectory_id     INTEGER,
    PRIMARY KEY (id)
);

ALTER TABLE res_cluster_capacity
    ADD CONSTRAINT "res_cluster_capacity_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

CREATE SEQUENCE res_cluster_capacity_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- changeset metienne:110V036-2
CREATE TABLE res_load_factory
(
    id         INTEGER,
    ts_name VARCHAR(60) NOT NULL UNIQUE,
    checksum VARCHAR(255) NOT NULL,
    trajectory_id     INTEGER,
    PRIMARY KEY (id)
);

ALTER TABLE res_load_factory
    ADD CONSTRAINT "res_load_factory_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

CREATE SEQUENCE res_load_factory_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- changeset metienne:110V036-3
CREATE TABLE res_technology_distribution
(
    id                INTEGER,
    area              VARCHAR(20),
    groupe             VARCHAR(40),
    cluster           VARCHAR(40),
    pecd_zone         VARCHAR(10),
    pecd_technology   VARCHAR(10),
    capacity_by_year  numeric,
    trajectory_id     INTEGER,
    PRIMARY KEY (id)
);

ALTER TABLE res_technology_distribution
    ADD CONSTRAINT "res_technology_distribution_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

CREATE SEQUENCE res_technology_distribution_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- changeset metienne:110V036-4
CREATE TABLE res_zonal_distribution
(
    id                INTEGER,
    area              VARCHAR(20),
    groupe             VARCHAR(40),
    pecd_zone         VARCHAR(10),
    capacity_by_year  numeric,
    trajectory_id     INTEGER,
    PRIMARY KEY (id)
);

ALTER TABLE res_zonal_distribution
    ADD CONSTRAINT "res_zonal_distribution_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

CREATE SEQUENCE res_zonal_distribution_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


