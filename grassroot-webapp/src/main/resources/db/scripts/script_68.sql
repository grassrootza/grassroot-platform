alter table event add column log_book_id int8;
alter table event add column meeting_id int8;

alter table event add constraint FK_event_parent_log_book foreign key (log_book_id) references log_book;
alter table event add constraint FK_event_parent_meeting foreign key (meeting_id) references event;

alter table event_request add column log_book_id int8;
alter table event_request add column meeting_id int8;

alter table event_request add constraint FK_event_req_parent_log_book foreign key (log_book_id) references log_book;
alter table event_request add constraint FK_event_req_parent_meeting foreign key (meeting_id) references event;

alter table log_book add column event_id int8;
alter table log_book_request add column event_id int8;

ALTER TABLE log_book ALTER COLUMN group_id SET NOT NULL;

alter table log_book add constraint FK_log_book_parent_event foreign key (event_id) references event;
alter table log_book_request add constraint FK_log_book_req_parent_event foreign key (event_id) references event;