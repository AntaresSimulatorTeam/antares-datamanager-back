insert into pegase_local_db_schema.project (id, name, created_by, creation_date, description)
values
    (2, 'Bilan previsionnel 2023', 'Taher benjelloun amine', '2024-08-25 10:09:41.000000', 'description2023'),
        (1, 'Bilan previsionnel 2027', 'MOUAD Paris test', '2024-10-25 10:09:41.000000', 'Lorem ipsum dolor sit amet, consectetur adipiscing elit. Curabitur auctor massa sed libero convallis, non bibendum erat scelerisque. Integer pharetra lacus id nisl sagittis, ut volutpat enim lacinia. Morbi placerat, nisi non hendrerit gravida, sapien risus dictum eros, sit amet volutpat justo libero et orci. Quisque vehicula mauris et quam facilisis, sit amet fringilla purus pharetra. Vestibulum dictum felis nec tristique consequat. Nullam feugiat'),
        (3, 'Bilan previsionnel 2025', 'zayd guillaume pegase', '2024-07-25 10:09:41.000000', 'In the world of software development, achieving perfection is a journey rather than a destination. Every line of code written, reviewed, and refactored serves as a stepping stone toward that elusive ideal. As developers, we strive to balance functionality, readability, and performance, knowing that each compromise shapes the outcome. It is this relentless pursuit of excellence that defines the craft, driving innovation and progress'),
        (4, 'Bilan previsionnel 2041', 'Khalil reporting', '2024-04-25 10:09:41.000000','description1 description2, description1 description2 '),
        (5, 'Bilan previsionnel 2030', 'Cedric mco', '2024-03-25 10:09:41.000000', 'energy solar 2045 2078'),
        (6, 'Bilan previsionnel 2031', 'Jabrane SEER', '2024-01-25 10:09:41.000000', 'Xyz abc123 jklmnopqrstu vwxyz!@#$%^&*()_+1234567890-=<>?;:''[]{}|~qwertyuiopasdfghjklzxcvbnm QWERTYUIOPASDFGHJKLZXCVBNM0987654321?><:!@#$%^&*()_=+LoremIpsumDolorSitAmetConsecteturAdipiscingElit1234567890QwertyQazWsxEdcRfvTgbYhnUjmIkOlPplokijuhygtfredcxswqaz0987654321MNBVCXZLKJHGFDSAPOIUYTREWQ+=-[]{}|;'':",.<>?/qaz123wsx'),
        (7, 'Bilan previsionnel34 2033', 'Jawad reporting', '2025-01-25 10:09:41.000000', 'test test test test test'),
        (8, 'Bilanrtt previsionnel 2029', 'Khalil reporting', '2022-07-25 10:09:41.000000', 'rapport rapport'),
        (9, 'Bilan previsionnel 2029', 'Khalil reporting', '2024-09-25 10:09:41.000000', 'lala lala lala lala lala lala lala'),
        (10, 'Bilane previsionnel 2030', 'Cedric mco', '2024-04-25 10:09:41.000000', 'mco France Germany Italy Storage'),
        (11, 'Bilan previsionnel45 2022', 'Jabrane SEER', '2024-07-25 10:09:41.000000', 'Vehicle energy battery'),
        (12 ,'Bilan previsionnel 2033', 'Jawad reporting', '2024-06-25 10:09:41.000000', 'run pump hydro solar solar solar'),
        (14, 'Bilantt previsionnel 2029', 'Khalil reporting', '2025-03-25 10:09:41.000000', 'rain rain rain rain rain rain rain rain rain sun sun sun sun'),
        (16, 'Bilan previsionnel 2022', 'Jabrane SEER', '2024-10-25 10:09:41.000000', ''),
        (15 ,'Bilan previsionnel 2063', 'Jawad reporting', '2024-10-26 10:09:41.000000', 'test rest api test study solar'),
        (13, 'Bilan previsionnel 2040', 'Khalil reporting', '2024-07-25 10:09:41.000000', 'time series time series time series time series')
;

insert into pegase_local_db_schema.scenario (id, name, created_by,status,horizon,creation_date,project_id)
values  (1, 'BP23A_LIVV4_2023_REF', 'Guillaume arthrotomies', 'IN_PROGRESS', '2030-2031', '2024-07-25 10:07:21.000000', 1),
        (4, 'BP23A_LIVV4_2030_REF', 'mouad boukabba', 'IN_PROGRESS', '2030-2031', '2024-07-25 10:07:21.000000', 2),
        (2, 'BP23A_LIVV4_2026_REF', 'Hamid belmhdi', 'IN_PROGRESS', '2030-2031', '2024-07-25 10:07:21.000000', 1),
        (7, 'BP23A_LIVV4_2033_REF', 'Jawad reporting', 'IN_PROGRESS', '2030-2031', '2024-07-25 10:07:21.000000', 3),
        (3, 'BP23A_LIVV4_2021_REF', 'jamal eddine reda', 'GENERATED', '2030-2031', '2024-07-25 10:07:21.000000', 1),
        (8, 'BP23A_LIVV4_2029_REF', 'Khalil reporting', 'GENERATED', '2030-2031', '2024-07-25 10:07:21.000000', 3),
        (6, 'BP23A_LIVV4_2022_REF', 'Jabrane SEER', 'GENERATED', '2030-2031', '2024-07-25 10:07:21.000000', 2),
        (5, 'BP23A_LIVV4_2032_REF', 'Cedric mco', 'GENERATED', '2030-2031', '2024-07-25 10:07:21.000000', 2),
        (9, 'BP23A_LIVV4_2032_REF', 'Diego Rivera', 'ERROR', '2030-2031', '2024-07-25 10:07:21.000000', 2);

insert into pegase_local_db_schema.trajectory (id, file_name, file_size, checksum, type, version, created_by, creation_date, last_modification_content_date, horizon)
values  (3, 'areas_BP23_A_ref_v2', 6822, 'd73a71ca53c7952eb99ab46eec3aeb24fda4d109652f0f539581227124c25010', 'AREA', 1, 'zayd', '2024-07-22 15:13:56.860045', '2024-07-09 10:55:27.467000','2023-2024'),
        (4, 'areas_BP23_A_ref', 7132, 'bf64818cc16aa62110184b7e889188ce7cf0ee6a8b0f04a7721209bbd64c4b46', 'AREA', 1, 'MOUAD', '2024-07-22 14:52:31.234005', '2024-07-22 12:38:14.614000','2025-2026'),
        (2, 'links_BP23_A_ref', 12679, '55be2a4685154fa0a5c45e5bac70a637fbb15a354d58afd5ff56b2c61b5be082', 'LINK', 1, 'mouad', '2024-07-22 14:52:56.325083', '2024-05-28 16:19:51.429000', '2030-2031'),
        (1, 'links_BP23_A_ref', 13000, '55be2a4685154fa0a5c45e5bac70a637fbb15a354d58afd5ff56b2c4b5be082', 'LINK', 1, 'carmen', '2024-08-22 10:52:56.325083', '2024-09-01 18:00:01.429000', '2030-2031');


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


insert into pegase_local_db_schema.scenario_trajectory (scenario_id, trajectory_id)
values  (1, 1),
        (1, 2),
        (1, 3),
        (2, 1),
        (3, 2);