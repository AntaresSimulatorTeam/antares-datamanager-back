insert into pegase_local_db_schema.project (id, name, created_by, creation_date)
values
    (2, 'Bilan previsionnel 2023', 'Taher benjelloun amine', '2024-07-25 10:09:41.000000'),
        (1, 'Bilan previsionnel 2027', 'MOUAD Paris test', '2024-07-25 10:09:41.000000'),
        (3, 'Bilan previsionnel 2025', 'zayd guillaume pegase', '2024-07-25 10:09:41.000000'),
        (4, 'Bilan previsionnel 2029', 'Khalil reporting', '2024-07-25 10:09:41.000000'),
        (5, 'Bilan previsionnel 2030', 'Cedric mco', '2024-07-25 10:09:41.000000'),
        (6, 'Bilan previsionnel 2022', 'Jabrane SEER', '2024-07-25 10:09:41.000000'),
        (7, 'Bilan previsionnel 2033', 'Jawad reporting', '2024-07-25 10:09:41.000000'),
        (8, 'Bilan previsionnel 2029', 'Khalil reporting', '2024-07-25 10:09:41.000000'),
        (9, 'Bilan previsionnel 2029', 'Khalil reporting', '2024-07-25 10:09:41.000000'),
        (10, 'Bilan previsionnel 2030', 'Cedric mco', '2024-07-25 10:09:41.000000'),
        (11, 'Bilan previsionnel 2022', 'Jabrane SEER', '2024-07-25 10:09:41.000000'),
        (12 ,'Bilan previsionnel 2033', 'Jawad reporting', '2024-07-25 10:09:41.000000'),
        (14, 'Bilan previsionnel 2029', 'Khalil reporting', '2024-07-25 10:09:41.000000'),
        (16, 'Bilan previsionnel 2022', 'Jabrane SEER', '2024-07-25 10:09:41.000000'),
        (15 ,'Bilan previsionnel 2033', 'Jawad reporting', '2024-07-25 10:09:41.000000'),
        (13, 'Bilan previsionnel 2029', 'Khalil reporting', '2024-07-25 10:09:41.000000')
;

insert into pegase_local_db_schema.scenario (id, name, created_by,status,horizon,creation_date,project_id)
values  (1, 'BP23A_LIVV4_2023_REF', 'Guillaume arthrotomies', 'IN_PROGRESS', '2030-2031', '2024-07-25 10:07:21.000000', 1),
        (4, 'BP23A_LIVV4_2030_REF', 'mouad boukabba', 'IN_PROGRESS', '2030-2031', '2024-07-25 10:07:21.000000', 2),
        (2, 'BP23A_LIVV4_2026_REF', 'Hamid belmhdi', 'IN_PROGRESS', '2030-2031', '2024-07-25 10:07:21.000000', 1),
        (7, 'BP23A_LIVV4_2033_REF', 'Jawad reporting', 'IN_PROGRESS', '2030-2031', '2024-07-25 10:07:21.000000', 3),
        (3, 'BP23A_LIVV4_2021_REF', 'jamal eddine reda', 'GENERATED', '2030-2031', '2024-07-25 10:07:21.000000', 1),
        (8, 'BP23A_LIVV4_2029_REF', 'Khalil reporting', 'GENERATED', '2030-2031', '2024-07-25 10:07:21.000000', 3),
        (6, 'BP23A_LIVV4_2022_REF', 'Jabrane SEER', 'GENERATED', '2030-2031', '2024-07-25 10:07:21.000000', 2),
        (5, 'BP23A_LIVV4_2032_REF', 'Cedric mco', 'GENERATED', '2030-2031', '2024-07-25 10:07:21.000000', 2);

insert into pegase_local_db_schema.trajectory (id, file_name, file_size, checksum, type, version, created_by, creation_date, last_modification_content_date, horizon)
values  (3, 'areas_BP23_A_ref_v2', 6822, 'd73a71ca53c7952eb99ab46eec3aeb24fda4d109652f0f539581227124c25010', 'AREA', 1, 'zayd', '2024-07-22 15:13:56.860045', '2024-07-09 10:55:27.467000','2023-2024'),
        (4, 'areas_BP23_A_ref', 7132, 'bf64818cc16aa62110184b7e889188ce7cf0ee6a8b0f04a7721209bbd64c4b46', 'AREA', 1, 'MOUAD', '2024-07-22 14:52:31.234005', '2024-07-22 12:38:14.614000','2025-2026'),
        (2, 'links_BP23_A_ref', 12679, '55be2a4685154fa0a5c45e5bac70a637fbb15a354d58afd5ff56b2c61b5be082', 'LINK', 1, 'mouad', '2024-07-22 14:52:56.325083', '2024-05-28 16:19:51.429000', '2030-2031');

insert into pegase_local_db_schema.scenario_trajectory (scenario_id, trajectory_id)
values  (1, 4),
        (1, 2),
        (1, 3),
        (2, 4),
        (3, 2);

INSERT INTO pegase_local_db_schema.scenario_tags (scenario_id, tag)
values  (3, 'config'),
        (1, 'elec'),
        (3, 'antares'),
        (3, 'bilan 22'),
        (1, 'gaz'),
        (2, 'figma');

INSERT INTO pegase_local_db_schema.project_tags (project_id, tag)
values  (1, 'gaz'),
        (1, 'elec'),
        (3, 'figma'),
        (1, 'antares'),
        (3, 'config'),
        (2, 'bilan 22'),
        (3, 'modal'),
        (1, 'misc'),
        (1, 'tag2 antares'),
        (1, ' area link');

insert into pegase_local_db_schema.pinned_project (nni,project_id)
values  ('me00247', 1),
        ('me00247', 2),
        ('me00247', 3),
        ('no0099', 1),
        ('no0099', 2);