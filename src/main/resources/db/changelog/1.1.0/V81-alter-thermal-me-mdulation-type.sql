-- liquibase formatted sql

-- changeset metienne:110V81-1
-- 1. Modifier le type des colonnes de modulation
ALTER TABLE thermal_me
    ALTER COLUMN marginal_cost_modulation TYPE NUMERIC;

ALTER TABLE thermal_me
    ALTER COLUMN market_bid_cost_modulation TYPE NUMERIC;

ALTER TABLE thermal_me
    ALTER COLUMN mr_modulation TYPE NUMERIC;

ALTER TABLE thermal_me
    ALTER COLUMN cm_modulation TYPE NUMERIC;