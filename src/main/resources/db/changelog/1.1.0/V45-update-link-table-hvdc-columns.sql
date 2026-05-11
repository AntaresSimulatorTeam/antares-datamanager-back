-- liquibase formatted sql
-- changeset salemsd:110V045-1

-- Update LINKS table schema for HVDC

ALTER TABLE link DROP COLUMN IF EXISTS specific_ts;
ALTER TABLE link DROP COLUMN IF EXISTS forced_outage_hvac;

-- Change existing columns
ALTER TABLE link ALTER COLUMN hvdc DROP DEFAULT;
ALTER TABLE link DROP COLUMN hvdc;
ALTER TABLE link ADD COLUMN hvdc_mw_direct NUMERIC;

-- Add new HVDC columns
ALTER TABLE link ADD COLUMN hvdc_mw_indirect NUMERIC;
ALTER TABLE link ADD COLUMN hvdc_nb NUMERIC;
ALTER TABLE link ADD COLUMN hvdcfo_rate NUMERIC;
ALTER TABLE link ADD COLUMN hvdc BOOLEAN DEFAULT FALSE;

