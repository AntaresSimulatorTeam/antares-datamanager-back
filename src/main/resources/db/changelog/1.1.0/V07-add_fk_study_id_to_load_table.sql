-- liquibase formatted sql
-- changeset elazaarmou:110V07-1

ALTER TABLE load
    ADD COLUMN study_id INTEGER DEFAULT NULL;
ALTER TABLE load
    ADD CONSTRAINT "load_FK2" FOREIGN KEY (study_id) REFERENCES scenario (id);