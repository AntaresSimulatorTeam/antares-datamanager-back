-- liquibase formatted sql
-- changeset metienne:110V063-1

ALTER TABLE scenario ADD COLUMN recalculate BOOLEAN DEFAULT FALSE NOT NULL;