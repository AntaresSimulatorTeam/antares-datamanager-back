-- liquibase formatted sql
-- changeset vargastat:110V028-1

ALTER TABLE area_config DROP COLUMN power_to_gas;
ALTER TABLE area_config DROP COLUMN short_term_storage;

ALTER TABLE area_config ADD COLUMN district VARCHAR(20);
ALTER TABLE area_config ADD COLUMN spilled_energy_cost NUMERIC;
ALTER TABLE area_config ADD COLUMN unsupplied_energy_cost NUMERIC;