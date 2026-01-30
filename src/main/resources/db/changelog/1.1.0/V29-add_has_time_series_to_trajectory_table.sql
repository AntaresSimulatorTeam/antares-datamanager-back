-- liquibase formatted sql
-- changeset etiennemar:110V029-1
ALTER TABLE trajectory ADD COLUMN has_time_series BOOLEAN;