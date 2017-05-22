create table live_wire_log (
  id bigserial not null,
  uid varchar(50) not null,
  creation_time timestamp not null,
  type varchar(50) not null,
  alert_id bigint,
  subscriber_id bigint,
  user_acting_id bigint,
  user_targeted_id bigint,
  notes varchar(255),
  primary key (id)
);

alter table live_wire_log add constraint uk_live_wire_log_uid unique (uid);
alter table live_wire_log add constraint fk_lwire_log_alert foreign key (alert_id) references live_wire_alert;
alter table live_wire_log add constraint fk_lwire_log_subscriber foreign key (subscriber_id) references data_subscriber;
alter table live_wire_log add constraint fk_lwire_log_user_acting foreign key (user_acting_id) references user_profile;
alter table live_wire_log add constraint fk_lwire_log_user_targeted foreign key (user_targeted_id) references user_profile;

alter table notification add column livewire_log_id bigint;
alter table notification add constraint fk_notification_livewire_log foreign key (livewire_log_id) references live_wire_log;