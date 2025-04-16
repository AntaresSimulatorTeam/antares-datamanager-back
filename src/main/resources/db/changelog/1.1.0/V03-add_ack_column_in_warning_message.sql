-- liquibase formatted sql
-- changeset elazaarmou:110V03-1
ALTER TABLE warning_message ADD COLUMN ack BOOLEAN DEFAULT FALSE NOT NULL;