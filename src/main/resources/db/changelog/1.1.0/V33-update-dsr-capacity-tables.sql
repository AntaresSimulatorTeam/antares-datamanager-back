-- liquibase formatted sql
-- changeset elazaarmou:110V033-1
-- Drop old columns if present (safe for already applied changelog)
DROP INDEX IF EXISTS dsr_cm_trajectory_area_cluster_idx;

ALTER TABLE dsr_capacity_modulation DROP COLUMN IF EXISTS date_time;
ALTER TABLE dsr_capacity_modulation DROP COLUMN IF EXISTS area_cluster_name;
ALTER TABLE dsr_capacity_modulation DROP COLUMN IF EXISTS capacity_value;

-- Add new columns for TS name and checksum
ALTER TABLE dsr_capacity_modulation ADD COLUMN IF NOT EXISTS ts_name VARCHAR(40);
ALTER TABLE dsr_capacity_modulation ADD COLUMN IF NOT EXISTS checksum VARCHAR(255);

-- Recreate index: drop old index if exists and create new one
CREATE INDEX IF NOT EXISTS dsr_cm_trajectory_tsname_idx ON dsr_capacity_modulation (trajectory_id, ts_name);