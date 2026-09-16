-- liquibase formatted sql

-- changeset elazaarmou:110V73-1
CREATE TABLE group_area_desc
(
    id            INTEGER     NOT NULL,
    group_name    VARCHAR(60) NOT NULL,
    trajectory_id INTEGER     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT group_area_desc_fk_trajectory
        FOREIGN KEY (trajectory_id) REFERENCES trajectory (id)
);

CREATE SEQUENCE group_area_desc_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE list_area_desc
(
    id            INTEGER     NOT NULL,
    area          VARCHAR(20) NOT NULL,
    group_area_id INTEGER     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT list_area_desc_fk_group
        FOREIGN KEY (group_area_id) REFERENCES group_area_desc (id)
            ON DELETE CASCADE,
    CONSTRAINT list_area_desc_uk_group_area
        UNIQUE (group_area_id, area)
);

CREATE SEQUENCE list_area_desc_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- changeset elazaarmou:110V73-2
CREATE TABLE group_cluster_desc
(
    id            INTEGER     NOT NULL,
    group_name    VARCHAR(60) NOT NULL,
    trajectory_id INTEGER     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT group_cluster_desc_fk_trajectory
        FOREIGN KEY (trajectory_id) REFERENCES trajectory (id)
);

CREATE SEQUENCE group_cluster_desc_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE list_cluster_desc
(
    id               INTEGER     NOT NULL,
    cluster           VARCHAR(40) NOT NULL,
    group_cluster_id  INTEGER     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT list_cluster_desc_fk_group
        FOREIGN KEY (group_cluster_id) REFERENCES group_cluster_desc (id)
            ON DELETE CASCADE,
    CONSTRAINT list_cluster_desc_uk_group_cluster
        UNIQUE (group_cluster_id, cluster)
);

CREATE SEQUENCE list_cluster_desc_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- changeset elazaarmou:110V73-3
CREATE TABLE me_constraint
(
    id                          INTEGER,
    name                        VARCHAR(40),
    enabled                     BOOLEAN,
    sign                        VARCHAR(20),
    temporality                 VARCHAR(20),
    type                        VARCHAR(5) DEFAULT 'G2P',
    comments                    VARCHAR(200),
    noeud1_gauche               VARCHAR(60),
    noeud2_gauche               VARCHAR(60),
    cluster_gauche              VARCHAR(60),
    noeud1_droite               VARCHAR(60),
    noeud2_droite               VARCHAR(60),
    cluster_droite              VARCHAR(60),
    trajectory_id               INTEGER,
    PRIMARY KEY (id)
);

ALTER TABLE me_constraint
    ADD CONSTRAINT "me_constraint_FK1"
        FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

CREATE SEQUENCE me_constraint_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;