-- Create sequence and res_type table, then insert default values
CREATE SEQUENCE IF NOT EXISTS res_type_sequence START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS res_type (
    id INTEGER PRIMARY KEY,
    label VARCHAR(255) NOT NULL
);

ALTER TABLE res_type
    ALTER COLUMN id SET DEFAULT nextval('res_type_sequence');

INSERT INTO res_type (label) VALUES ('Wind Offshore');
INSERT INTO res_type (label) VALUES ('Wind Onshore');
INSERT INTO res_type (label) VALUES ('Solar PV');
INSERT INTO res_type (label) VALUES ('Solar Thermo');

