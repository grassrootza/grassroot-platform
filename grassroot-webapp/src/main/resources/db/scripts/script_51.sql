create table group_join_request (
id  bigserial not null,
creation_time timestamp not null,
processed_time timestamp,
status varchar(50) not null,
uid varchar(50) not null,
group_id int8 not null,
requestor_id int8 not null,
primary key (id));

alter table group_join_request add constraint uk_group_join_request_uid unique (uid);
alter table group_join_request add constraint fk_group_join_request_group foreign key (group_id) references group_profile;
alter table group_join_request add constraint fk_group_join_requestor foreign key (requestor_id) references user_profile;

create table group_join_request_event (
id  bigserial not null,
occurrence_time timestamp not null,
type varchar(50) not null,
uid varchar(50) not null,
request_id int8 not null,
user_id int8 not null,
primary key (id));

alter table group_join_request_event add constraint uk_group_join_req_event_uid unique (uid);
alter table group_join_request_event add constraint fk_group_join_req_event_request foreign key (request_id) references group_join_request;
alter table group_join_request_event add constraint fk_group_join_req_event_user foreign key (user_id) references user_profile;

