alter table event alter column importance type varchar(50) using cast(importance as varchar);
alter table event alter column importance drop default;
update event set importance = 'ORDINARY' where type = 'MEETING';
update event set importance = null where type = 'VOTE';