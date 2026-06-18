-- liquibase formatted sql
-- changeset vargas:110V53-1

ALTER TABLE link DROP COLUMN IF EXISTS hvdc;

ALTER TABLE scenario ADD COLUMN hvdc BOOLEAN DEFAULT FALSE;