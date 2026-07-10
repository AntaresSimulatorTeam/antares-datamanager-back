-- liquibase formatted sql
-- changeset salemsd:110V056-1

ALTER TABLE hydro_allocation ALTER COLUMN allocation TYPE NUMERIC;