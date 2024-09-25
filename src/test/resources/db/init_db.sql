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
insert into public.trajectory(id,file_name,file_size,checksum,type,version,created_by,creation_date,last_modification_content_date,horizon)
values (1,'testFile.txt',100,'123','AREA',1,'test','2024-06-22 19:10:25-07','2024-06-22 19:12:25-07','2023-2024'),
       (2,'testFile.txt',100,'123','AREA',2,'test','2024-06-23 19:10:25-07','2024-06-23 19:12:25-07','2025-2026');

insert into public.thermal_cost_type(id,country,fuel,scenario,comment,unit,modulation,ratio_ncv_hcv)
values (1,'Morocco','GAS','etude1','comment1','MWh','modulation1',1.1),
       (2,'Morocco','OIL','etude2','comment2','MWh','modulation2',1.2),
       (3,'Morocco','OIL','etude3','comment3','MWh','modulation3',1.3),
       (4,'europe','OIL','etude4','comment4','MWh','modulation4',1.4),
       (5,'europe','GAS','etude5','comment5','MWh','modulation5',1.5),
       (6,'FRANCE','GAS','etude6','comment6','MWh','modulation6',1.6),
       (7,'FRANCE','GAS','etude7','comment7','MWh','modulation7',1.7),
       (8,'SPAIN','GAS','etude8','comment8','MWh','modulation8',1.8);

insert into public.area(id,name,x,y,r,g,b)
 values (1,'area1',1,2,3,4,5),
        (2,'area2',1,2,3,4,5),
        (3,'area3',1,2,3,4,5),
        (4,'area4',1,2,3,4,5),
        (5,'area5',1,2,3,4,5),
        (6,'area6',1,2,3,4,5),
        (7,'area7',1,2,3,4,5),
        (8,'area8',1,2,3,4,5);

