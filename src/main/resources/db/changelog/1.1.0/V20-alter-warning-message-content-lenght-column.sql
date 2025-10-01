-- liquibase formatted sql
-- changeset elazaarmou:110V20-01

ALTER TABLE warning_message ALTER COLUMN warning_content TYPE VARCHAR(500);
