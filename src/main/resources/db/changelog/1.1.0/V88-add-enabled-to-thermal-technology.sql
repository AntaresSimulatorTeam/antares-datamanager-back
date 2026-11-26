-- liquibase formatted sql

-- changeset copilot:V88-1
ALTER TABLE thermal_technology ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- changeset copilot:V88-2
UPDATE thermal_technology SET enabled = FALSE WHERE UPPER(name) = 'TAC';

-- changeset copilot:V88-3
-- Each insert takes MAX(id) + 1 at execution time, so ids are not hardcoded
INSERT INTO thermal_technology (id, name, enabled)
SELECT (SELECT COALESCE(MAX(id), 0) FROM thermal_technology) + 1, 'CHP', TRUE
WHERE NOT EXISTS (SELECT 1 FROM thermal_technology WHERE UPPER(name) = 'CHP');

-- changeset copilot:V88-4
INSERT INTO thermal_technology (id, name, enabled)
SELECT (SELECT COALESCE(MAX(id), 0) FROM thermal_technology) + 1, 'Reserves', TRUE
WHERE NOT EXISTS (SELECT 1 FROM thermal_technology WHERE UPPER(name) = 'RESERVES');

-- changeset copilot:V88-5
INSERT INTO thermal_technology (id, name, enabled)
SELECT (SELECT COALESCE(MAX(id), 0) FROM thermal_technology) + 1, 'Biomass', TRUE
WHERE NOT EXISTS (SELECT 1 FROM thermal_technology WHERE UPPER(name) = 'BIOMASS');
