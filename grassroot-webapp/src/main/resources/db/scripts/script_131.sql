create table image_record (
  id bigserial not null,
  action_log_type varchar(50) not null,
  action_log_uid varchar(50) not null,
  auxiliary varchar(255),
  bucket varchar(255) not null,
  creation_time timestamp not null,
  md5_hash varchar(24),
  stored_time timestamp,
  primary key (id));

alter table image_record add constraint uk_image_action_log_uid_type unique (action_log_type, action_log_uid);