-- liquibase formatted sql
-- changeset metienne:110V51-1

UPDATE default_load SET is_default = false WHERE name = 'OTHERS';
