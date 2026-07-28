-- liquibase formatted sql
-- changeset metienne:110V062-1

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
    name                                VARCHAR(40),
    trajectory_id                       INTEGER,
    PRIMARY KEY (id)
);

ALTER TABLE fb_virtual_nodes
    ADD CONSTRAINT "fb_virtual_nodes_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

CREATE SEQUENCE fb_virtual_nodes_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE fb_link_capacity
(
    id                                  INTEGER,
    name                                VARCHAR(20),
    winter_HP_direct_MW                 INTEGER,
    winter_HP_indirect_MW               INTEGER,
    winter_HC_direct_MW                 INTEGER,
    winter_HC_indirect_MW               INTEGER,
    summer_HP_direct_MW                 INTEGER,
    summer_HP_indirect_MW               INTEGER,
    summer_HC_direct_MW                 INTEGER,
    summer_HC_indirect_MW               INTEGER,
    hurdles_cost                        BOOLEAN DEFAULT FALSE NOT NULL,
    trajectory_id                       INTEGER,
    PRIMARY KEY (id)
);

ALTER TABLE fb_link_capacity
    ADD CONSTRAINT "fb_link_capacity_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

CREATE SEQUENCE fb_link_capacity_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE fb_type_day
(
    id                          INTEGER,
    clustering                  VARCHAR(20),
    id_type_day                 INTEGER NOT NULL,
    class_day                   VARCHAR(20),
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