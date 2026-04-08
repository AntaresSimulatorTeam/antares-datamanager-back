-- liquibase formatted sql
-- changeset metienne:110V041-1
ALTER TABLE res_technology_distribution ALTER COLUMN pecd_technology TYPE VARCHAR(40);