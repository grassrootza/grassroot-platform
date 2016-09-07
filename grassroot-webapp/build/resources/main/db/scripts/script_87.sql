delete from prev_period_user_location;
alter table prev_period_user_location add column log_count int4 not null;

create table group_location (
  id  bigserial not null,
  local_date timestamp not null,
  latitude float8 not null,
  longitude float8 not null,
  score float4 not null,
  group_id int8 not null,
  primary key (id)
);

alter table group_location add constraint uk_group_location_group_date unique (group_id, local_date);
alter table group_location add constraint fk_group_location_group foreign key (group_id) references group_profile;

