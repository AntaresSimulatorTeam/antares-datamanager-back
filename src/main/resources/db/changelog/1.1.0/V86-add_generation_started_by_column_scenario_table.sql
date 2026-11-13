-- liquibase formatted sql
-- changeset elazaarmou:110V085-1

ALTER TABLE scenario ADD COLUMN generation_started_by VARCHAR(255);
ALTER TABLE scenario ALTER COLUMN  status  TYPE VARCHAR(60);
