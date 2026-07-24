-- liquibase formatted sql
-- changeset salemsd:110V061-1


-- The update cluster_designation (V60) was missing p4

INSERT INTO cluster (type_cluster) VALUES ('p4');

DELETE FROM cluster_designation
WHERE cluster_id = (SELECT id FROM cluster WHERE type_cluster = 'n4')
  AND nom_cluster IN (
    'BVIL7N01', 'BVIL7N02', 'CATTEN01', 'CATTEN02', 'CATTEN03', 'CATTEN04',
    'FLAMAN01', 'FLAMAN02', 'GOLF5N01', 'GOLF5N02', 'N.SE5N01', 'N.SE5N02',
    'PALUEN01', 'PALUEN02', 'PALUEN03', 'PALUEN04', 'PENLYN01', 'PENLYN02',
    'SSAL7N01', 'SSAL7N02'
  );

INSERT INTO cluster_designation (cluster_id, nom_cluster)
SELECT (SELECT id FROM cluster WHERE type_cluster = 'p4'), v.nom_cluster
FROM (VALUES
    ('BVIL7N01'), ('BVIL7N02'), ('CATTEN01'), ('CATTEN02'), ('CATTEN03'), ('CATTEN04'),
    ('FLAMAN01'), ('FLAMAN02'), ('GOLF5N01'), ('GOLF5N02'), ('N.SE5N01'), ('N.SE5N02'),
    ('PALUEN01'), ('PALUEN02'), ('PALUEN03'), ('PALUEN04'), ('PENLYN01'), ('PENLYN02'),
    ('SSAL7N01'), ('SSAL7N02')
) AS v(nom_cluster);
