-- liquibase formatted sql
-- changeset elazaarmou:110V08-1
CREATE TABLE trajectory_load (
                                 id_trajectory BIGINT NOT NULL,
                                 id_load BIGINT NOT NULL,
                                 PRIMARY KEY (id_trajectory, id_load),
                                 CONSTRAINT fk_trajectory_load_trajectory FOREIGN KEY (id_trajectory) REFERENCES trajectory(id),
                                 CONSTRAINT fk_trajectory_load_load FOREIGN KEY (id_load) REFERENCES load(id)
);

INSERT INTO trajectory_load (id_trajectory, id_load)
SELECT trajectory_id, id FROM load WHERE trajectory_id IS NOT NULL;

ALTER TABLE load DROP COLUMN trajectory_id;
ALTER TABLE load DROP COLUMN study_id;