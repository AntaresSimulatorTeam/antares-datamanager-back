-- liquibase formatted sql
-- changeset elazaarmou:100V14-1
ALTER TABLE warning_message
ADD COLUMN study_id INT;

ALTER TABLE warning_message
ADD COLUMN second_trajectory_id INT;

ALTER TABLE warning_message
ADD CONSTRAINT "study_id_FK1" FOREIGN KEY (study_id) REFERENCES scenario (id);

ALTER TABLE warning_message
ADD CONSTRAINT "second_trajectory_id_FK1" FOREIGN KEY (second_trajectory_id) REFERENCES trajectory (id);
