-- liquibase formatted sql
-- changeset salem:100V10-1
CREATE TABLE load
(
    id INT NOT NULL,
    trajectory_id       INT,
    PRIMARY KEY (id),
    FOREIGN KEY (trajectory_id) REFERENCES trajectory (id)
);

ALTER TABLE trajectory ADD load INT;
ALTER TABLE trajectory ADD CONSTRAINT trajectory_load_fkey FOREIGN KEY (load) REFERENCES load (id);

CREATE SEQUENCE load_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;