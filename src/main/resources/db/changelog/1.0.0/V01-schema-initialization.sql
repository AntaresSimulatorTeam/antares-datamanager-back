-- liquibase formatted sql

-- changeset elazaarmou:100V1-1
CREATE TABLE project
(
    id            INTEGER,
    name          VARCHAR(255),
    created_by    VARCHAR(255),
    creation_date timestamp,
    PRIMARY KEY (id)
);

-- changeset elazaarmou:100V1-2
CREATE TABLE scenario
(
    id            INTEGER,
    name          VARCHAR(255),
    created_by    VARCHAR(255),
    creation_date timestamp,
    PRIMARY KEY (id)
);

-- changeset elazaarmou:100V1-3
CREATE TABLE trajectory
(
    id                             INTEGER,
    file_name                      VARCHAR(255) NOT NULL,
    file_size                      numeric,
    checksum                       VARCHAR(255),
    type                           VARCHAR(255),
    version                        numeric,
    created_by                     VARCHAR(255),
    creation_date                  timestamp,
    last_modification_content_date timestamp,
    PRIMARY KEY (id)
);

-- changeset elazaarmou:100V1-4
CREATE TABLE project_scenario
(
    scenario_id INTEGER NOT NULL,
    project_id  INTEGER NOT NULL,
    CONSTRAINT "project_scenario_PK" PRIMARY KEY (scenario_id, project_id)
);

ALTER TABLE project_scenario
    ADD CONSTRAINT "scenario_fk" FOREIGN KEY (scenario_id) REFERENCES scenario (id);

ALTER TABLE project_scenario
    ADD CONSTRAINT "project_fk" FOREIGN KEY (project_id) REFERENCES project (id);

-- changeset elazaarmou:100V1-5
CREATE TABLE scenario_trajectory
(
    scenario_id   INTEGER NOT NULL,
    trajectory_id INTEGER NOT NULL,
    CONSTRAINT "scenario_trajectory_PK" PRIMARY KEY (scenario_id, trajectory_id)
);

ALTER TABLE scenario_trajectory
    ADD CONSTRAINT "scenario_fk2" FOREIGN KEY (scenario_id) REFERENCES scenario (id);

ALTER TABLE scenario_trajectory
    ADD CONSTRAINT "trajectory_fk" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

-- changeset elazaarmou:100V1-6
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

-- changeset elazaarmou:100V1-7
CREATE TABLE area_config
(
    id                 INTEGER,
    power_to_gas       BOOLEAN DEFAULT false,
    short_term_storage BOOLEAN DEFAULT false,
    area_id            INTEGER,
    trajectory_id      INTEGER,
    PRIMARY KEY (id)
);
-- changeset elazaarmou:100V1-8
ALTER TABLE area_config
    ADD CONSTRAINT "area_config_FK1" FOREIGN KEY (area_id) REFERENCES area (id);

-- changeset elazaarmou:100V1-9
ALTER TABLE area_config
    ADD CONSTRAINT "area_config_FK2" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);
-- changeset elazaarmou:100V1-10
CREATE INDEX area_config_FK1_idx
    ON area_config (area_id);

-- changeset elazaarmou:100V1-11
CREATE INDEX area_config_FK2_idx
    ON area_config (trajectory_id);

-- changeset elazaarmou:100V1-12
insert into area(id, name, x, y, r, g, b)
values (1, 'AT', 400, -50, 255, 128, 128),
       (2, 'BE', 25, -25, 66, 0, 0),
       (3, 'CH', 150, -75, 66, 0, 0);
-- changeset elazaarmou:100V1-13

CREATE
SEQUENCE area_config_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
 -- changeset elazaarmou:100V1-14

  CREATE
SEQUENCE trajectory_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


 -- changeset elazaarmou:100V1-15

  CREATE
SEQUENCE area_sequence
    START WITH 40
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;




      -- changeset elazaarmou:100V1-16
CREATE TABLE link
(
    id                    INTEGER,
    name                  VARCHAR(255),
    winter_hp_direct_mw   INTEGER,
    winter_hp_Indirect_mw INTEGER,
    winter_hc_direct_mw   INTEGER,
    winter_hc_indirect_mw INTEGER,
    summer_hp_direct_mw   INTEGER,
    summer_hp_indirect_MW INTEGER,
    summer_hc_direct_mw   INTEGER,
    summer_hc_indirect_mw INTEGER,
    flowbased_perimeter   BOOLEAN DEFAULT false,
    hvdc                  BOOLEAN DEFAULT false,
    specific_ts           BOOLEAN DEFAULT false,
    forced_outage_hvac    BOOLEAN DEFAULT false,
    hurdle_cost           numeric,
    trajectory_id         INTEGER,
    PRIMARY KEY (id)
);
-- changeset elazaarmou:100V1-17
ALTER TABLE link
    ADD CONSTRAINT "link_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);
-- changeset elazaarmou:100V1-18
CREATE INDEX link_FK1_idx
    ON area_config (trajectory_id);


CREATE
SEQUENCE link_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

