-- liquibase formatted sql
-- changeset salemsd:110V060-1

-- The initial cluster_designation (V47) was missing n4

INSERT INTO cluster (type_cluster) VALUES ('n4');

DELETE FROM cluster_designation
WHERE cluster_id = (SELECT id FROM cluster WHERE type_cluster = 'cp0_cp1_cp2')
  AND nom_cluster IN (
    'CHOO2N01', 'CHOO2N02', 'CIVAUN01', 'CIVAUN02',
    'BVIL7N01', 'BVIL7N02', 'CATTEN01', 'CATTEN02', 'CATTEN03', 'CATTEN04',
    'FLAMAN01', 'FLAMAN02', 'GOLF5N01', 'GOLF5N02', 'N.SE5N01', 'N.SE5N02',
    'PALUEN01', 'PALUEN02', 'PALUEN03', 'PALUEN04', 'PENLYN01', 'PENLYN02',
    'SSAL7N01', 'SSAL7N02'
  );

INSERT INTO cluster_designation (cluster_id, nom_cluster)
SELECT (SELECT id FROM cluster WHERE type_cluster = 'n4'), v.nom_cluster
FROM (VALUES
    ('CHOO2N01'), ('CHOO2N02'), ('CIVAUN01'), ('CIVAUN02'),
    ('BVIL7N01'), ('BVIL7N02'), ('CATTEN01'), ('CATTEN02'), ('CATTEN03'), ('CATTEN04'),
    ('FLAMAN01'), ('FLAMAN02'), ('GOLF5N01'), ('GOLF5N02'), ('N.SE5N01'), ('N.SE5N02'),
    ('PALUEN01'), ('PALUEN02'), ('PALUEN03'), ('PALUEN04'), ('PENLYN01'), ('PENLYN02'),
    ('SSAL7N01'), ('SSAL7N02')
) AS v(nom_cluster);
