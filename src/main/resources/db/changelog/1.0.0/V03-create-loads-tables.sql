-- liquibase formatted sql

-- changeset elazaarmou:100V3-1
CREATE TABLE link
(
    id                    INTEGER,
    name                  VARCHAR(255),
    winter_hp_direct_mw   INTEGER,
    winter_hp_Indirect_mw INTEGER,
    winter_hc_direct_mw   INTEGER,
    winter_hc_indirect_mw INTEGER,
    summer_hp_direct_mw   INTEGER,
    summer_hp_indirect_MW INTEGER,
    summer_hc_direct_mw   INTEGER,
    summer_hc_indirect_mw INTEGER,
    flowbased_perimeter   BOOLEAN DEFAULT false,
    hvdc                  BOOLEAN DEFAULT false,
    specific_ts           BOOLEAN DEFAULT false,
    forced_outage_hvac    BOOLEAN DEFAULT false,
    hurdle_cost           numeric,
    trajectory_id         INTEGER,
    PRIMARY KEY (id)
);
-- changeset elazaarmou:100V3-2
ALTER TABLE link
    ADD CONSTRAINT "link_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);



CREATE
SEQUENCE link_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
