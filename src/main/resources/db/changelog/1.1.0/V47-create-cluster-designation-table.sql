-- liquibase formatted sql
-- changeset elazaarmou:110V047-1
-- Create sequence for cluster table
CREATE SEQUENCE IF NOT EXISTS cluster_sequence START WITH 1 INCREMENT BY 1;

-- Create cluster table
CREATE TABLE IF NOT EXISTS cluster (
    id INTEGER PRIMARY KEY,
    type_cluster VARCHAR(20) NOT NULL UNIQUE
);

ALTER TABLE cluster
    ALTER COLUMN id SET DEFAULT nextval('cluster_sequence');

-- Create cluster_designation table with composite primary key (cluster_id, nom_cluster)
CREATE TABLE IF NOT EXISTS cluster_designation (
    cluster_id INTEGER NOT NULL,
    nom_cluster VARCHAR(20) NOT NULL,
    PRIMARY KEY (cluster_id, nom_cluster),
    FOREIGN KEY (cluster_id) REFERENCES cluster(id)
);

-- Insert cluster types
INSERT INTO cluster (type_cluster) VALUES ('EPR');
INSERT INTO cluster (type_cluster) VALUES ('cp0_cp1_cp2');

-- Insert cluster designations for EPR (cluster_id = 1)
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (1, 'FLAMAN03');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (1, 'EPR01');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (1, 'EPR02');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (1, 'EPR03');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (1, 'EPR04');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (1, 'EPR05');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (1, 'EPR06');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (1, 'EPR07');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (1, 'EPR08');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (1, 'EPR09');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (1, 'EPR10');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (1, 'EPR11');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (1, 'EPR12');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (1, 'EPR13');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (1, 'EPR14');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (1, 'EPR15');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (1, 'EPR16');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (1, 'EPR17');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (1, 'EPR18');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (1, 'EPR19');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (1, 'EPR20');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (1, 'EPR21');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (1, 'EPR22');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (1, 'EPR23');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (1, 'EPR24');

-- Insert cluster designations for cp0_cp1_cp2 (cluster_id = 2)
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'BLAYAN01');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'BLAYAN02');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'BLAYAN03');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'BLAYAN04');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'BUGEYN02');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'BUGEYN03');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'BUGEYN04');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'BUGEYN05');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'CHIN2N01');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'CHIN2N02');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'CHIN2N03');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'CHIN2N04');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'CRUA5N01');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'CRUA5N02');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'CRUA5N03');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'CRUA5N04');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'D.BURN01');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'D.BURN02');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'D.BURN03');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'D.BURN04');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'FESS5N01');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'FESS5N02');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'GRAV5N01');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'GRAV5N02');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'GRAV5N03');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'GRAV5N04');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'GRAV5N05');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'GRAV5N06');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'SSEA2N01');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'SSEA2N02');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'TRICAN01');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'TRICAN02');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'TRICAN03');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'TRICAN04');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'CHOO2N01');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'CHOO2N02');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'CIVAUN01');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'CIVAUN02');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'BVIL7N01');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'BVIL7N02');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'CATTEN01');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'CATTEN02');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'CATTEN03');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'CATTEN04');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'FLAMAN01');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'FLAMAN02');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'GOLF5N01');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'GOLF5N02');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'N.SE5N01');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'N.SE5N02');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'PALUEN01');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'PALUEN02');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'PALUEN03');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'PALUEN04');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'PENLYN01');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'PENLYN02');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'SSAL7N01');
INSERT INTO cluster_designation (cluster_id, nom_cluster) VALUES (2, 'SSAL7N02');

