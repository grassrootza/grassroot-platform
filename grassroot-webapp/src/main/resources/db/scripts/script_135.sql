create table meeting_location (
  id  bigserial not null,
  calculated_time timestamp not null,
  latitude float8 not null,
  longitude float8 not null,
  score float4 not null,
  event_id int8 not null,
  event_type varchar(50) not null,
  primary key (id)
);

alter table meeting_location add constraint uk_meeting_location_event_date unique (event_id, calculated_time);
alter table meeting_location add constraint fk_meeting_location_event foreign key (event_id) references event;

alter table paid_account add column events_per_month integer;
update paid_account set events_per_month = 8 where account_type = 'LIGHT';
update paid_account set events_per_month = 10 where account_type = 'STANDARD';
update paid_account set events_per_month = 15 where account_type = 'HEAVY';
update paid_account set events_per_month = 4 where events_per_month is null;
alter table paid_account alter column events_per_month set default 0;