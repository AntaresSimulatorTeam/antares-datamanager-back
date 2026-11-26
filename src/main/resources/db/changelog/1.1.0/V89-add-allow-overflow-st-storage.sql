-- liquibase formatted sql
-- changeset vargastat:V89-1
ALTER TABLE st_storage ADD COLUMN allow_overflow BOOLEAN;
UPDATE st_storage SET allow_overflow = FALSE;
