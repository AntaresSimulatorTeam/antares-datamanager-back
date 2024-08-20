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
    status        VARCHAR(255),
    horizon       VARCHAR(255),
    creation_date timestamp,
    project_id    INTEGER,
    PRIMARY KEY (id)
);

ALTER TABLE scenario
    ADD CONSTRAINT "scenario_fk1" FOREIGN KEY (project_id) REFERENCES project (id);


CREATE TABLE scenario_tags
(
    scenario_id INT NOT NULL,
    tag         VARCHAR(255),
    FOREIGN KEY (scenario_id) REFERENCES scenario (id)
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