-- liquibase formatted sql
-- changeset vargas_tat:110V1-1
CREATE TABLE default_load
(
    id   INTEGER,
    name VARCHAR(10) NOT NULL,
    is_default BOOLEAN,
    entity VARCHAR(10),
    PRIMARY KEY (id)
);
-- changeset vargas_tat:110V1-2
insert into default_load(id, name, is_default, entity)
values (1, 'FR', TRUE, 'PPSE'),
       (2, 'OTHERS', TRUE, 'PPSE');
