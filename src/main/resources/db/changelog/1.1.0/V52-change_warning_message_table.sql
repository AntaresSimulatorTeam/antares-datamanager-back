-- liquibase formatted sql
-- changeset elazaarmou:110V52-1

ALTER TABLE warning_message
ALTER COLUMN warning_content TYPE TEXT;