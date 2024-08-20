-- liquibase formatted sql
-- changeset elazaarmou:100V2-1
CREATE TABLE area
(
    id   INTEGER,
    name VARCHAR(10) NOT NULL,
    x    INTEGER,
    y    INTEGER,
    r    INTEGER,
    g    INTEGER,
    b    INTEGER,
    PRIMARY KEY (id)
);

-- changeset elazaarmou:100V2-2
CREATE TABLE area_config
(
    id                 INTEGER,
    power_to_gas       BOOLEAN DEFAULT false,
    short_term_storage BOOLEAN DEFAULT false,
    area_id            INTEGER,
    trajectory_id      INTEGER,
    PRIMARY KEY (id)
);
-- changeset elazaarmou:100V2-3
ALTER TABLE area_config
    ADD CONSTRAINT "area_config_FK1" FOREIGN KEY (area_id) REFERENCES area (id);

-- changeset elazaarmou:100V2-4
ALTER TABLE area_config
    ADD CONSTRAINT "area_config_FK2" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);
-- changeset elazaarmou:100V2-5
CREATE INDEX area_config_FK1_idx
    ON area_config (area_id);

-- changeset elazaarmou:100V2-6
CREATE INDEX area_config_FK2_idx
    ON area_config (trajectory_id);

-- changeset elazaarmou:100V2-7
insert into area(id, name, x, y, r, g, b)
values (1, 'AT', 400, -50, 255, 128, 128),
       (2, 'BE', 25, -25, 66, 0, 0),
       (3, 'CH', 150, -75, 66, 0, 0);
-- changeset elazaarmou:100V2-8

CREATE
SEQUENCE area_config_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
-- changeset elazaarmou:100V2-9

  CREATE
SEQUENCE trajectory_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


 -- changeset elazaarmou:100V2-10

  CREATE
SEQUENCE area_sequence
    START WITH 40
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- changeset elazaarmou:100V2-11
CREATE INDEX link_FK1_idx
    ON area_config (trajectory_id);