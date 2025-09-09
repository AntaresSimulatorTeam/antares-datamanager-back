-- liquibase formatted sql
-- changeset junie:110V17-1

ALTER TABLE trajectory ALTER COLUMN type TYPE VARCHAR(40);
