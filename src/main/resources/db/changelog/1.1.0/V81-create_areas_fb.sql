-- liquibase formatted sql
-- changeset metienne:110V081-1

CREATE TABLE areas_fb
(
    id            INTEGER PRIMARY KEY,
    area          VARCHAR(255) NOT NULL,
    trajectory_id INTEGER NOT NULL,
    CONSTRAINT areas_fb_trajectory_fk FOREIGN KEY (trajectory_id) REFERENCES trajectory (id)
);

CREATE SEQUENCE areas_fb_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
