ALTER TABLE log_book DROP COLUMN assigned_to_user_id;
ALTER TABLE log_book_request DROP COLUMN assigned_to_user_id;

create table log_book_assigned_members (
log_book_id int8 not null,
user_id int8 not null,
primary key (log_book_id, user_id));

create table log_book_request_assigned_members (
log_book_request_id int8 not null,
user_id int8 not null,
primary key (log_book_request_id, user_id));

create table event_assigned_members (
event_id int8 not null,
user_id int8 not null,
primary key (event_id, user_id));

create table event_request_assigned_members (
event_request_id int8 not null,
user_id int8 not null,
primary key (event_request_id, user_id));

alter table log_book_assigned_members add constraint FK_log_book_assigned_user foreign key (user_id) references user_profile;
alter table log_book_assigned_members add constraint FK_log_book_assigned_book foreign key (log_book_id) references log_book;
alter table log_book_request_assigned_members add constraint FK_log_book_request_assigned_user foreign key (user_id) references user_profile;
alter table log_book_request_assigned_members add constraint FK_log_book_request_assigned_request foreign key (log_book_request_id) references log_book_request;

alter table event_assigned_members add constraint FK_event_assigned_user foreign key (user_id) references user_profile;
alter table event_assigned_members add constraint FK_event_assigned_event foreign key (event_id) references event;
alter table event_request_assigned_members add constraint FK_event_request_assigned_user foreign key (user_id) references user_profile;
alter table event_request_assigned_members add constraint FK_event_request_assigned_request foreign key (event_request_id) references event_request;
