-- liquibase formatted sql
-- changeset salem:100V12-1

CREATE TABLE warning_message
(
    id                 INT NOT NULL,
    warning_code       VARCHAR(255),
    warning_level      VARCHAR(255),
    trajectory_id      INT,
    study_id           INT,
    PRIMARY KEY (id),
    FOREIGN KEY (trajectory_id) REFERENCES trajectory (id),
    FOREIGN KEY (study_id) REFERENCES scenario (id)
);

CREATE SEQUENCE warning_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
