-- liquibase formatted sql
-- changeset vargas_tat:110V23-1
CREATE TABLE trajectory_modulation_parameters (
                                 id_trajectory BIGINT NOT NULL,
                                 id_modulation_param BIGINT NOT NULL,
                                 PRIMARY KEY (id_trajectory, id_modulation_param),
                                 CONSTRAINT fk_trajectory_mod_param_trajectory  FOREIGN KEY (id_trajectory) REFERENCES trajectory(id),
                                 CONSTRAINT fk_trajectory_mod_param_param  FOREIGN KEY (id_modulation_param) REFERENCES thermal_modulation_parameters(id)
);

INSERT INTO trajectory_modulation_parameters(id_trajectory, id_modulation_param)
SELECT trajectory_id, id FROM thermal_modulation_parameters WHERE trajectory_id IS NOT NULL;
