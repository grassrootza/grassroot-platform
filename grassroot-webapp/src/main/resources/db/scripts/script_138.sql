create table live_wire_alert (
  id bigserial not null,
  uid varchar(50) not null,
  creation_time timestamp not null,
  created_by_user bigint not null,
  type varchar(50) not null,
  meeting_id bigint,
  group_id bigint,
  contact_user_id bigint,
  contact_name varchar(255),
  description varchar(255),
  send_time timestamp,
  sent boolean default false,
  latitude double precision,
  longitude double precision,
  location_source varchar(50),
  primary key (id)
);

alter table live_wire_alert add constraint uk_live_wire_alert_uid unique (uid);
alter table live_wire_alert add constraint fk_lwire_alert_created_by foreign key (created_by_user) references user_profile;
alter table live_wire_alert add constraint fk_lwire_alert_meeting foreign key (meeting_id) references event;
alter table live_wire_alert add constraint fk_lwire_alert_group foreign key (group_id) references group_profile;
alter table live_wire_alert add constraint fk_lwire_alert_contact_user foreign key (contact_user_id) references user_profile;