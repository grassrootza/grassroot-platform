create table user_location_log (
  id  bigserial not null,
  uid varchar(50) not null,
  user_uid varchar(50) not null,
  timestamp timestamp not null,
  latitude float8 not null,
  longitude float8 not null,
  primary key (id)
);
alter table user_location_log add constraint uk_user_location_log_uid unique (uid);

create table prev_period_user_location (
  local_time timestamp not null,
  user_uid varchar(50) not null,
  latitude float8 not null,
  longitude float8 not null,
  primary key (local_time, user_uid)
);

alter table event rename column meeting_id to parent_meeting_id;
alter table event_request rename column meeting_id to parent_meeting_id;
