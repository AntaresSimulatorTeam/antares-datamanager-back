-- liquibase formatted sql
-- changeset elazaarmou:110V04-1
ALTER TABLE trajectory ADD COLUMN area VARCHAR(20) DEFAULT NULL;