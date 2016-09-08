create table event_request (
type varchar(31) not null,
id  bigserial not null,
uid varchar(50),
name varchar(40),
created_date_time timestamp,
start_date_time timestamp,
description varchar(512),
can_relay boolean,
reminder_type varchar(50),
custom_reminder_minutes int4,
rsvprequired boolean,
includesubgroups boolean,
location varchar(50),
applies_to_group int8,
created_by_user int8,
primary key (id));

ALTER TABLE event ADD COLUMN description varchar(512);

