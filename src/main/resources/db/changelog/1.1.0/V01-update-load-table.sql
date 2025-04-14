-- liquibase formatted sql
-- changeset elazaarmou:110V01-1

-- Drop the foreign key constraint from the trajectory table
ALTER TABLE trajectory DROP CONSTRAINT trajectory_load_fkey;

-- Remove the load column from the trajectory table
ALTER TABLE trajectory DROP COLUMN load;

-- Drop the load table
DROP TABLE IF EXISTS load;

CREATE TABLE load
(
    id INT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    trajectory_id       INT,
    PRIMARY KEY (id),
    FOREIGN KEY (trajectory_id) REFERENCES trajectory (id)
);