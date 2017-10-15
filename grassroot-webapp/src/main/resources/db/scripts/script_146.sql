alter table paid_account add column charge_per_message boolean default false;

create table notification_template (
  id bigserial not null,
  uid varchar(50) not null,
  account_id bigint not null,
  group_id bigint,
  creation_time timestamp not null,
  created_by_user_id bigint not null,
  trigger_type varchar(50) not null,
  active boolean default true,
  only_use_free boolean default true,
  msg_template1 varchar(255) not null,
  msg_template2 varchar(255),
  msg_template3 varchar(255),
  language varchar(10),
  send_delay bigint not null default 0,
  primary key (id)
);

alter table only notification_template add constraint uk_n_template_uid unique (uid);

alter table only notification_template add constraint fk_ntemplate_account_id foreign key (account_id) references paid_account(id);

alter table only notification_template add constraint fk_ntemplate_group_id foreign key (group_id) references group_profile(id);
