-- liquibase formatted sql
-- changeset elazaarmou:100V13-1

ALTER TABLE project
ADD CONSTRAINT unique_project_name UNIQUE (name);
