-- liquibase formatted sql
-- changeset elazaarmou:100V7-1
CREATE SEQUENCE study_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

    CREATE SEQUENCE project_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;