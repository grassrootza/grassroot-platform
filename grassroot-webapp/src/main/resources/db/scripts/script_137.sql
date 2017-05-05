create table data_subscriber (
  id bigserial not null,
  active boolean,
  creation_time timestamp not null,
  primary_email varchar(255) not null,
  uid varchar(50) not null,
  administrator bigint not null,
  created_by_user bigint not null,
  push_emails text[] not null default '{}',
  access_users text[] not null default '{}',
  primary key (id)
);

alter table data_subscriber add constraint uk_data_subscriber_uid unique (uid);
alter table data_subscriber add constraint fk_dsub_admin foreign key (administrator) references user_profile;
alter table data_subscriber add constraint fk_dsub_created_by foreign key (created_by_user) references user_profile;

create index dsub_push_emails on data_subscriber using gin(push_emails);
create index dsub_user_access on data_subscriber using gin(access_users);

alter table address alter column resident_user_id drop not null;
alter table address add column latitude double precision;
alter table address add column longitude double precision;
alter table address add column location_source varchar(50);

create table address_log (
  id bigserial not null,
  creation_time timestamp not null,
  description varchar(255),
  latitude double precision not null,
  longitude double precision not null,
  source varchar(255) not null,
  uid varchar(50) not null,
  address_id bigint,
  primary key (id)
);

alter table address_log add constraint uk_address_log_uid unique (uid);
alter table address_log add constraint fk_address_log_address foreign key (address_id) references address;