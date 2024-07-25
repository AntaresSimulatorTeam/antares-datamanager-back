insert into public.project (id, name, created_by, creation_date)
values  (1, 'PROJECT1', 'MOUAD', '2024-07-25 10:09:41.000000'),
        (2, 'PROJECT2', 'ghita', '2024-07-25 10:09:41.000000'),
        (3, 'PROJECT3', 'zayd', '2024-07-25 10:09:41.000000');
insert into public.scenario (id, name, created_by, creation_date,status,horizon,project_id)
values  (1, 'etude1', 'mouad', '2024-07-25 10:07:21.000000','IN_PROGRESS','2030-2031',1),
        (2, 'etude2', 'mouad', '2024-07-25 10:07:21.000000','IN_PROGRESS','2030-2031',1),
        (3, 'etude3', 'zayd', '2024-07-25 10:07:21.000000','IN_PROGRESS','2030-2031',1),
        (4, 'etude4', 'zayd', '2024-07-25 10:07:21.000000','IN_PROGRESS','2030-2031',2),
        (5, 'etude5', 'zayd', '2024-07-25 10:07:21.000000','IN_PROGRESS','2030-2031',2),
        (6, 'etude6', 'zayd', '2024-07-25 10:07:21.000000','IN_PROGRESS','2030-2031',2),
        (7, 'etude7', 'ghita', '2024-07-25 10:07:21.000000','IN_PROGRESS','2030-2031',3),
        (8, 'etude8', 'ghita', '2024-07-25 10:07:21.000000','IN_PROGRESS','2030-2031',3);
insert into public.trajectory(id,file_name,file_size,checksum,type,version,created_by,creation_date,last_modification_content_date)
values (1,'testFile.txt',100,'123','AREA',1,'test','2024-06-22 19:10:25-07','2024-06-22 19:12:25-07'),
       (2,'testFile.txt',100,'123','AREA',2,'test','2024-06-23 19:10:25-07','2024-06-23 19:12:25-07');

