-- liquibase formatted sql
-- changeset vargas:110V056-1
CREATE TABLE adequacy_patch_mode
(
    id                INTEGER,
    area              VARCHAR(100) NOT NULL,
    mode              VARCHAR(40),
    trajectory_id     INTEGER,
    PRIMARY KEY (id)
);

ALTER TABLE adequacy_patch_mode
    ADD CONSTRAINT "adequacy_patch_mode_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

CREATE SEQUENCE adequacy_patch_mode_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


CREATE TABLE adequacy_patch_settings
(
    id                INTEGER,
    include_adq_patch BOOLEAN,
    price_taking_order VARCHAR(20),
    include_hurdle_cost_csr BOOLEAN,
    check_csr_cost_function BOOLEAN,
    threshold_initiate_curtailment_sharing_rule INTEGER,
    threshold_display_local_matching_rule_violations INTEGER,
    threshold_csr_variable_bounds_relaxation INTEGER,
    redispatch BOOLEAN,
    set_to_null_ntc_from_physical_areas_out_to_physical_areas_in_adequacy_patch BOOLEAN,
    trajectory_id     INTEGER,
    PRIMARY KEY (id)

);
ALTER TABLE adequacy_patch_settings
    ADD CONSTRAINT "adequacy_patch_settings_FK1" FOREIGN KEY (trajectory_id) REFERENCES trajectory (id);

CREATE SEQUENCE adequacy_patch_settings_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
