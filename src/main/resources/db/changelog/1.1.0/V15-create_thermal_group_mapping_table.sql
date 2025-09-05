-- liquibase formatted sql
-- changeset salemsd:110V15-1
CREATE TABLE thermal_group_mapping (
                                       id BIGINT PRIMARY KEY,
                                       cluster  TEXT NOT NULL,
                                       group_name  TEXT NOT NULL,
                                       UNIQUE (cluster)
);

CREATE SEQUENCE thermal_group_mapping_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER TABLE thermal_group_mapping
    ALTER COLUMN id SET DEFAULT nextval('thermal_group_mapping_sequence');
ALTER SEQUENCE thermal_group_mapping_sequence
    OWNED BY thermal_group_mapping.id;

INSERT INTO thermal_group_mapping (cluster, group_name) VALUES
                                                                   ('Nuclear', 'Nuclear'),
                                                                   ('Nuclear SMR', 'Nuclear'),

                                                                   ('Hard coal old 1', 'Hard coal'),
                                                                   ('Hard coal old 2', 'Hard coal'),
                                                                   ('Hard coal new',   'Hard coal'),
                                                                   ('Hard coal CCS',   'Hard coal'),

                                                                   ('Lignite old 1', 'Lignite'),
                                                                   ('Lignite old 2', 'Lignite'),
                                                                   ('Lignite new',   'Lignite'),
                                                                   ('Lignite CCS',   'Lignite'),

                                                                   ('Gas conventional old 1', 'Gas'),
                                                                   ('Gas conventional old 2', 'Gas'),
                                                                   ('CCGT old 1',             'Gas'),
                                                                   ('CCGT old 2',             'Gas'),
                                                                   ('CCGT present 1',         'Gas'),
                                                                   ('CCGT present 2',         'Gas'),
                                                                   ('CCGT new',               'Gas'),
                                                                   ('CCGT CCS',               'Gas'),
                                                                   ('OCGT old',               'Gas'),
                                                                   ('OCGT new',               'Gas'),

                                                                   ('Light oil',       'Oil'),
                                                                   ('Heavy oil old 1', 'Oil'),
                                                                   ('Heavy oil old 2', 'Oil'),
                                                                   ('Oil shale old',   'Oil'),
                                                                   ('Oil shale new',   'Oil'),

                                                                   ('Fuel cell', 'Fuel cell'),

                                                                   ('CCGT H2',      'H2'),
                                                                   ('OCGT H2',      'H2'),
                                                                   ('Gas pcomp mid','H2'),
                                                                   ('Gas pcomp peak','H2'),

                                                                   ('Other non identified',      'Gas'),
                                                                   ('Other Hard coal old 1',     'Hard coal'),
                                                                   ('Other Hard coal old 2',     'Hard coal'),
                                                                   ('Other Hard coal new',       'Hard coal'),
                                                                   ('Other Hard coal CCS',       'Hard coal'),
                                                                   ('Other Lignite old 1',       'Lignite'),
                                                                   ('Other Lignite old 2',       'Lignite'),
                                                                   ('Other Lignite new',         'Lignite'),
                                                                   ('Other Lignite CCS',         'Lignite'),
                                                                   ('Other Gas conventional old 1','Gas'),
                                                                   ('Other Gas conventional old 2','Gas'),
                                                                   ('Other CCGT old 1',          'Gas'),
                                                                   ('Other CCGT old 2',          'Gas'),
                                                                   ('Other CCGT present 1',      'Gas'),
                                                                   ('Other CCGT present 2',      'Gas'),
                                                                   ('Other CCGT new',            'Gas'),
                                                                   ('Other OCGT old',            'Gas'),
                                                                   ('Other Light oil',           'Oil'),
                                                                   ('Other Heavy oil old 2',     'Oil'),
                                                                   ('Other Oil shale old',       'Oil')

