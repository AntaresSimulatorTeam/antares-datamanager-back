-- liquibase formatted sql
-- changeset copilot:110V85-1
ALTER TABLE trajectory ALTER COLUMN file_name TYPE VARCHAR(255);
