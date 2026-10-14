-- liquibase formatted sql
-- changeset copilot:110V082-1

CREATE TABLE fb_constraints_restrictions
(
    id            INTEGER PRIMARY KEY,
    trajectory_id INTEGER NOT NULL,
    name          TEXT,
    enabled       BOOLEAN,
    sign          TEXT,
    temporality   TEXT,
    type          TEXT,
    list_links    TEXT,
    second_member TEXT,
    CONSTRAINT fb_constraints_restrictions_trajectory_fk
        FOREIGN KEY (trajectory_id) REFERENCES trajectory (id)
);

CREATE SEQUENCE fb_constraints_restrictions_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE fb_link_list
(
    id            INTEGER PRIMARY KEY,
    trajectory_id INTEGER NOT NULL,
    list_name     TEXT NOT NULL,
    link          TEXT NOT NULL,
    position      INTEGER NOT NULL,
    CONSTRAINT fb_link_list_trajectory_fk
        FOREIGN KEY (trajectory_id) REFERENCES trajectory (id),
    CONSTRAINT fb_link_list_trajectory_list_position_uk
        UNIQUE (trajectory_id, list_name, position)
);

CREATE INDEX fb_link_list_trajectory_list_idx
    ON fb_link_list (trajectory_id, list_name);

CREATE SEQUENCE fb_link_list_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
