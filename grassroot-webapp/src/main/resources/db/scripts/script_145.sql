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

create table livewire_media_files (
  livewire_alert_id bigint not null,
  media_file_id bigint not null,
  primary key(livewire_alert_id, media_file_id)
);

alter table only livewire_media_files add constraint fk_lwire_mfiles_alert foreign key (livewire_alert_id) references live_wire_alert(id);
alter table only livewire_media_files add constraint fk_lwire_mfiles_file foreign key (media_file_id) references media_file(id);

alter table only live_wire_alert rename column description to headline;
alter table only live_wire_alert add column description text;