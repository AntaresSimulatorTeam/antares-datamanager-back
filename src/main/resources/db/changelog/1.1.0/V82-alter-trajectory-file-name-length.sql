-- liquibase formatted sql
-- changeset elazaarmou:110V81-1
ALTER TABLE trajectory ALTER COLUMN file_name TYPE VARCHAR(60);
