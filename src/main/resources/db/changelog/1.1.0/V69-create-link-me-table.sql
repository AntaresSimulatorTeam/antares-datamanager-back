-- liquibase formatted sql
-- changeset melazaar:110V069-1

-- Create LINK_ME table
CREATE TABLE link_me
(
    id                      INTEGER,
    node_from               VARCHAR(60) NOT NULL,
    node_to                 VARCHAR(60) NOT NULL,
    direct_mw               NUMERIC,
    indirect_mw             NUMERIC,
    hurdle_costs_direct     NUMERIC,
    hurdle_costs_indirect   NUMERIC,
    trajectory_id           INTEGER,
    PRIMARY KEY (id)
);

-- Add foreign key constraint
ALTER TABLE link_me
    ADD CONSTRAINT "link_me_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

-- Create sequence for link_me
CREATE SEQUENCE link_me_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
