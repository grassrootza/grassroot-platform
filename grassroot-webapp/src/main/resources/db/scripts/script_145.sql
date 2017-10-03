create table media_file (
  id bigserial not null,
  uid varchar(50) not null,
  creation_time timestamp not null,
  stored_time timestamp not null,
  bucket varchar(50),
  key varchar(255),
  read_count bigint,
  mime_type varchar(50),
  md5_hash varchar(24),
  version integer not null default 0,
  primary key(id)
);

alter table only media_file add constraint uk_media_file_uid unique (uid);
alter table only media_file add constraint uk_media_file_bucket_key unique (bucket, key);

create table live_wire_media_files (
  live_wire_alert_id bigint not null,
  media_file_id bigint not null,
  primary key(live_wire_alert_id, media_file_id)
);

alter table only live_wire_media_files add constraint fk_lwire_mfiles_alert foreign key (live_wire_alert_id) references live_wire_alert(id);
alter table only live_wire_media_files add constraint fk_lwire_mfiles_file foreign key (media_file_id) references media_file(id);

alter table only live_wire_alert rename column description to headline;
alter table only live_wire_alert add column description text;

-- denormalizing--given how often we use this property, alternative was very frequent, messy joins
alter table only group_profile add column last_task_creation_time timestamp without time zone;
alter table only group_profile add column last_log_creation_time timestamp without time zone;

update group_profile set last_log_creation_time = (select max(gl.created_date_time) from group_log gl
  where gl.group_id = group_profile.id);

update group_profile set last_task_creation_time =
(select max(t.created_date_time) from action_todo t where t.ancestor_group_id = group_profile.id);

update group_profile set last_task_creation_time = greatest(last_task_creation_time,
  (select max(e.created_date_time) from event e where e.ancestor_group_id = group_profile.id));