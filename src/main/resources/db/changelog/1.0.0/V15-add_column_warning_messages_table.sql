-- liquibase formatted sql
-- changeset elazaarmou:100V15-1
ALTER TABLE warning_message
ADD COLUMN created_by varchar(40);

ALTER TABLE warning_message
ADD COLUMN creation_date TIMESTAMP;
