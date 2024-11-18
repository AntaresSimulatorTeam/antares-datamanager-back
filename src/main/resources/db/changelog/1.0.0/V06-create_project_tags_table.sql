-- liquibase formatted sql
-- changeset elazaarmou:100V6-1
CREATE TABLE project_tags
(
    project_id INT NOT NULL,
    tag         VARCHAR(15),
    FOREIGN KEY (project_id) REFERENCES project (id)
);