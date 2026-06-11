-- liquibase formatted sql
-- changeset vargas:110V52-1

INSERT INTO thermal_technology (id, name)
VALUES
    (9, 'OCGT');



ALTER TABLE thermal_specific_parameters
DROP CONSTRAINT IF EXISTS thermal_specific_parameters_FK2;

ALTER TABLE thermal_specific_parameters
DROP COLUMN IF EXISTS thermal_cluster_ref_id;
ALTER TABLE thermal_specific_parameters DROP COLUMN IF EXISTS node_entsoe;

ALTER TABLE thermal_specific_parameters ADD COLUMN cluster VARCHAR(255);