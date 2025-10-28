-- liquibase formatted sql
-- changeset elazaarmou:110V22-1
CREATE SEQUENCE IF NOT EXISTS thermal_costs_rate_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS thermal_economic_co2_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS thermal_economic_ener_content_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;


-- Table thermal_costs_rate
CREATE TABLE IF NOT EXISTS thermal_costs_rate
(
    id            BIGINT PRIMARY KEY,
    trajectory_id BIGINT         NOT NULL ,
    rate_type     VARCHAR(50),
    rate_year          INT            NOT NULL,
    rate_value         NUMERIC(18, 6) NOT NULL
);
ALTER TABLE thermal_costs_rate
    ADD CONSTRAINT "thermal_costs_rate_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

-- Table thermal_economic_co2
CREATE TABLE IF NOT EXISTS thermal_economic_co2
(
    id                BIGINT PRIMARY KEY,
    trajectory_id     BIGINT         NOT NULL ,
    fuel              VARCHAR(100)   NOT NULL,
    country           VARCHAR(100),
    co2_emission_year              INT            NOT NULL,
    co2_emission_fuel NUMERIC(18, 6) NOT NULL,
    unit_co2          VARCHAR(50),
    comment           TEXT
);
ALTER TABLE thermal_economic_co2
    ADD CONSTRAINT "thermal_economic_co2_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

-- Table thermal_economic_ener_content
CREATE TABLE IF NOT EXISTS thermal_economic_ener_content
(
    id            BIGINT PRIMARY KEY,
    trajectory_id BIGINT         NOT NULL ,
    ener_value         NUMERIC(18, 6) NOT NULL,
    unit          VARCHAR(50),
    comment       TEXT
);
ALTER TABLE thermal_economic_ener_content
    ADD CONSTRAINT "thermal_economic_ener_content_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

