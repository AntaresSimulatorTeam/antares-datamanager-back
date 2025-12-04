-- liquibase formatted sql
-- changeset vargas_tat:110V25-1
-- Remove old values
TRUNCATE TABLE thermal_technology CASCADE;

INSERT INTO thermal_technology (id, name)
VALUES
    (1, 'CCGT'),
    (2, 'Additional power'),
    (3, 'TAC'),
    (4, 'Other'),
    (5, 'Coal_Lignite'),
    (6, 'Nuclear');
