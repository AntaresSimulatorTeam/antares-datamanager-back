-- liquibase formatted sql

-- changeset elazaarmou:110V74-1
CREATE TABLE efficiency_me
(
    id              INTEGER         NOT NULL,
    node_cluster    VARCHAR(60)     NOT NULL,
    type            VARCHAR(10),
    comments        VARCHAR(200),
    efficiency      NUMERIC         NOT NULL,
    trajectory_id   INTEGER         NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT efficiency_me_fk_trajectory
        FOREIGN KEY (trajectory_id) REFERENCES trajectory (id)
);

ALTER TABLE efficiency_me
    ADD CONSTRAINT efficiency_me_uk_node_cluster_trajectory
        UNIQUE (node_cluster, trajectory_id);

CREATE SEQUENCE efficiency_me_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
