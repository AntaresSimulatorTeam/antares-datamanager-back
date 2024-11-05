-- liquibase formatted sql
-- changeset elazaarmou:100V4-1
CREATE TABLE pinned_project
(
    nni VARCHAR(10),
    project_id INTEGER,
    PRIMARY KEY (nni, project_id)
);
-- changeset elazaarmou:1004-2
ALTER TABLE pinned_project ADD CONSTRAINT "FKslwwg8q5ydg60g4h6sclgajh8" FOREIGN KEY (project_id) REFERENCES project (id);