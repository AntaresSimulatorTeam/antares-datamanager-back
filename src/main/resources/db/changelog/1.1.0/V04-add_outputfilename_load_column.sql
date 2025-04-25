-- liquibase formatted sql
-- changeset elazaarmou:110V04-1
ALTER TABLE load ADD COLUMN output_file_name  VARCHAR(100);
