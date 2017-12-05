create table group_observe_request (
  id bigserial not null,
  uid varchar(50) not null,
  creation_time timestamp not null,
  description varchar(255),
  processed_time timestamp,
  status varchar(50) not null,
  version integer,
  group_id bigint not null,
  observer_id bigint not null,
  tags text[] default '{}',
  primary key (id));

alter table group_observe_request add constraint uk_grp_observe_request_uid unique (uid);
alter table group_observe_request add constraint fk_grp_observe_observer foreign key (observer_id) references user_profile;
alter table group_observe_request add constraint fk_grp_observe_group foreign key (group_id) references group_profile;
create index grp_obs_tag on group_observe_request using gin(tags);

create table user_mgmt_request (
  id bigserial not null,
  uid varchar(50) not null,
  creation_time timestamp not null,
  description varchar(255),
  processed_time timestamp,
  status varchar(50) not null,
  version integer,
  manager_id bigint not null,
  managed_user_id bigint not null,
  tags text[] default '{}',
  primary key (id));

alter table user_mgmt_request add constraint uk_user_mgmt_request_uid unique (uid);
alter table user_mgmt_request add constraint fk_user_mgr_requestor foreign key (manager_id) references user_profile;
alter table user_mgmt_request add constraint fk_user_mgr_managed foreign key (managed_user_id) references user_profile;
create index user_mgmt_tag on user_mgmt_request using gin(tags);

ALTER TABLE group_user_membership ADD COLUMN view_priority VARCHAR(50);

update group_user_membership set view_priority = 'NORMAL';
alter table group_user_membership alter column view_priority set not null;
create index membership_view_priority on group_user_membership (view_priority);