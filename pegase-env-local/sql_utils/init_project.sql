insert into pegase_local_db_schema.project (id, name, created_by, creation_date)
values  (1, 'Bilan Prévisionnel 2030-2031', 'Leonnel Messi barcelone', '2024-07-25 10:09:41.000000'),
        (2, 'Bilan Prévisionnel 2031-2032', 'xavi hernandes  teo', '2024-07-25 10:09:41.000000'),
        (3, 'Bilan Prévisionnel 2032-2033', 'Meknes rex hamria', '2024-07-25 10:09:41.000000');

insert into pegase_local_db_schema.scenario (id, name, created_by, creation_date,status,horizon,project_id)
values  (1, 'etude1', 'mouad', '2024-07-25 10:07:21.000000','IN_PROGRESS','2030-2031',1),
        (2, 'etude2', 'mouad', '2024-07-25 10:07:21.000000','IN_PROGRESS','2030-2031',1),
        (3, 'etude3', 'zayd', '2024-07-25 10:07:21.000000','IN_PROGRESS','2030-2031',1),
        (4, 'etude4', 'zayd', '2024-07-25 10:07:21.000000','IN_PROGRESS','2030-2031',2),
        (5, 'etude5', 'zayd', '2024-07-25 10:07:21.000000','IN_PROGRESS','2030-2031',2),
        (6, 'etude6', 'zayd', '2024-07-25 10:07:21.000000','IN_PROGRESS','2030-2031',2),
        (7, 'etude7', 'ghita', '2024-07-25 10:07:21.000000','IN_PROGRESS','2030-2031',3),
        (8, 'etude8', 'ghita', '2024-07-25 10:07:21.000000','IN_PROGRESS','2030-2031',3);

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
VALUES
    (1, 'tag1'),
    (1, 'tag2'),
    (2, 'tag3'),
    (3, 'tag1'),
    (3, 'tag2'),
    (3, 'tag3');

INSERT INTO pegase_local_db_schema.scenario_tags (scenario_id, tag)
VALUES
    (1, 'tag1'),
    (1, 'tag2'),
    (2, 'tag3'),
    (3, 'tag1'),
    (3, 'tag2'),
    (3, 'tag3');

INSERT INTO pegase_local_db_schema.project_tags (project_id, tag)
VALUES
    (1, 'tag1'),
    (1, 'tag2'),
    (1, 'tag2'),
    (1, 'tag2'),
    (1, 'tag2'),
    (1, 'tag1'),
    (1, 'tag2'),
    (1, 'tag2'),
    (1, 'tag2'),
    (1, 'tag2'),
    (2, 'tag3'),
    (3, 'tag1'),
    (3, 'tag2'),
    (3, 'tag3');

insert into pegase_local_db_schema.pinned_project (nni,project_id)
values  ('me00247', 1),
        ('me00247', 2),
        ('me00247', 3),
        ('no0099', 1),
        ('no0099', 2);