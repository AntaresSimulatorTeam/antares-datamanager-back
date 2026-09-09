-- liquibase formatted sql
-- changeset elazaarmou:110V71-1
ALTER TABLE load DROP COLUMN output_file_name;

-- changeset elazaarmou:110V71-2
ALTER TABLE load ALTER COLUMN area TYPE VARCHAR(40);
