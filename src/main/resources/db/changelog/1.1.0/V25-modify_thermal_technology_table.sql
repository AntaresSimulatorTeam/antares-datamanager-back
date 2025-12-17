-- liquibase formatted sql

-- changeset vargas_tat:110V25-1-postgresql dbms:postgresql
-- Remove old values (PostgreSQL)
TRUNCATE TABLE thermal_technology CASCADE;

-- changeset vargas_tat:110V25-1-h2 dbms:h2
-- Remove old values (H2: disable FKs, delete, re-enable)
SET REFERENTIAL_INTEGRITY FALSE;
DELETE FROM thermal_technology;
SET REFERENTIAL_INTEGRITY TRUE;

-- changeset vargas_tat:110V25-2
-- Re-seed values (common)
INSERT INTO thermal_technology (id, name)
VALUES
    (1, 'CCGT'),
    (2, 'Additional power'),
    (3, 'TAC'),
    (4, 'Other'),
    (5, 'Coal and lignite'),
    (6, 'Nuclear'),
    (7, 'Gas conventional'),
    (8, 'Heavy oil');
