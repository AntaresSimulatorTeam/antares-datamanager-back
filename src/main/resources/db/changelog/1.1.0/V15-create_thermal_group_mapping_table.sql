-- liquibase formatted sql
-- changeset salemsd:110V15-1
CREATE TABLE thermal_group_mapping (
                                       id BIGINT PRIMARY KEY,
                                       source_value  TEXT NOT NULL,
                                       pemmdb_group  TEXT NOT NULL,
                                       UNIQUE (source_value)
);

CREATE SEQUENCE thermal_group_mapping_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

INSERT INTO thermal_group_mapping (source_value, pemmdb_group)
VALUES
    ('CCGT'),
    ( 'Coal'),
    ( 'DSR'),
    ( 'Nuclear'),
    ( 'Puissance complémentaire'),
    ( 'Storage battery'),
    ( 'Storage EV'),
    ( 'TBD');