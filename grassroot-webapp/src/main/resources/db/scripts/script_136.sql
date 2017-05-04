alter table group_location add column source varchar(50);
update group_location set source = 'CALCULATED';
alter table group_location alter column source set not null;

alter table user_location_log add column source varchar(50);
update user_location_log set source = 'LOGGED_PRECISE';
alter table user_location_log alter column source set not null;

alter table meeting_location add column source varchar(50);
-- setting this to calculated to be safe (as lowest accuracy), table should be mostly empty anyway
update meeting_location set source = 'CALCULATED';
alter table meeting_location alter column source set not null;

alter table event_log add column location_source varchar(50);
alter table action_todo_log add column location_source varchar(50);

alter table safety_event add column latitude double precision;
alter table safety_event add column longitude double precision;
alter table safety_event add column location_source varchar(50);