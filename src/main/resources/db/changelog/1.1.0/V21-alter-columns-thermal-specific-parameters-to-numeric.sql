-- liquibase formatted sql
-- changeset vargas_tat:110V21-1
ALTER TABLE thermal_specific_parameters ALTER COLUMN p1 TYPE NUMERIC USING p1::numeric;
ALTER TABLE thermal_specific_parameters ALTER COLUMN p2 TYPE NUMERIC USING p2::numeric;
ALTER TABLE thermal_specific_parameters ALTER COLUMN p3 TYPE NUMERIC USING p3::numeric;
ALTER TABLE thermal_specific_parameters ALTER COLUMN p4 TYPE NUMERIC USING p4::numeric;
ALTER TABLE thermal_specific_parameters ALTER COLUMN p5 TYPE NUMERIC USING p5::numeric;
ALTER TABLE thermal_specific_parameters ALTER COLUMN p6 TYPE NUMERIC USING p6::numeric;
ALTER TABLE thermal_specific_parameters ALTER COLUMN p7 TYPE NUMERIC USING p7::numeric;
ALTER TABLE thermal_specific_parameters ALTER COLUMN p8 TYPE NUMERIC USING p8::numeric;
ALTER TABLE thermal_specific_parameters ALTER COLUMN p9 TYPE NUMERIC USING p9::numeric;
ALTER TABLE thermal_specific_parameters ALTER COLUMN p10 TYPE NUMERIC USING p10::numeric;
ALTER TABLE thermal_specific_parameters ALTER COLUMN p11 TYPE NUMERIC USING p11::numeric;
ALTER TABLE thermal_specific_parameters ALTER COLUMN p12 TYPE NUMERIC USING p12::numeric;
