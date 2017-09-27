create table acc_sponsor_request (
  id bigserial not null,
  creation_time timestamp not null,
  description varchar(255),
  processed_time timestamp,
  status varchar(50) not null,
  uid varchar(50) not null,
  version integer,
  destination_id bigint not null,
  requestor_id bigint not null, primary key (id));

alter table group_join_request add column version integer;

alter table acc_sponsor_request add constraint uk_acc_sponsor_request_uid unique (uid);
alter table acc_sponsor_request add constraint fk_acc_sponsor_destination foreign key (destination_id) references user_profile;
alter table acc_sponsor_request add constraint fk_acc_sponsor_requestor foreign key (requestor_id) references paid_account;

alter table group_join_request_event rename to association_request_event;
alter sequence group_join_request_id_seq rename to association_request_event_id_seq;
alter table association_request_event rename constraint uk_group_join_req_event_uid to uk_assoc_req_event_uid;
alter table association_request_event rename constraint fk_group_join_req_event_user to fk_assoc_req_event_user;

alter table association_request_event add column request_uid varchar(50);
alter table association_request_event add column request_type varchar(50);
alter table association_request_event add column aux_description varchar(255);
alter table association_request_event alter column user_id drop not null;

update association_request_event set request_uid = request.uid from group_join_request as request where request_id = request.id;
alter table association_request_event alter column request_uid set not null;
alter table association_request_event drop column request_id;

update association_request_event set request_type = 'GROUP_JOIN';
alter table association_request_event alter column request_type set not null;

create index idx_assoc_req_event_req_uid on association_request_event(request_uid);