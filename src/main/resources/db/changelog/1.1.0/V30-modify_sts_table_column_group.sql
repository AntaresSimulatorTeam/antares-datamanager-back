-- liquibase formatted sql
-- changeset vargas:301_modify_sts_table_column_group

ALTER TABLE st_storage RENAME COLUMN groupe TO "group";