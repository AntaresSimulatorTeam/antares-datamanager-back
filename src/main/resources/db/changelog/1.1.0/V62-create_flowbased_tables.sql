-- liquibase formatted sql
-- changeset metienne:110V060-1

CREATE TABLE fb_second_member
(
    id                         INTEGER,
    id_day                     INTEGER,
    id_hour                    INTEGER,
    vect_b                     INTEGER,
    name                       VARCHAR(20),
    trajectory_id              INTEGER,
    PRIMARY KEY (id)
);

ALTER TABLE fb_second_member
    ADD CONSTRAINT "fb_second_member_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

CREATE SEQUENCE fb_second_member_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE fb_type_day
(
    id                          INTEGER,
    clusterin                   VARCHAR(20),
    class                       VARCHAR(20),
    trajectory_id               INTEGER,
    PRIMARY KEY (id)
);

ALTER TABLE fb_type_day
    ADD CONSTRAINT "fb_type_day_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

CREATE SEQUENCE fb_type_day_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE fb_links_weight
(
    id                            INTEGER,
    weight                        VARCHAR(20),
    link                          VARCHAR(20) NOT NULL,
    trajectory_id                 INTEGER,
    PRIMARY KEY (id)
);

ALTER TABLE fb_links_weight
    ADD CONSTRAINT "fb_links_weight_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

CREATE SEQUENCE fb_links_weight_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE fb_virtual_nodes
(
    id                                  INTEGER,
    name                                INTEGER,
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

