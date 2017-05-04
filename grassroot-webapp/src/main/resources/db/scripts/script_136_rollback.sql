alter table group_location drop column source;
alter table user_location_log drop column source;
alter table meeting_location drop column source;

alter table event_log drop column location_source;
alter table action_todo_log drop column location_source;

alter table safety_event drop column latitude;
alter table safety_event drop column longitude;
alter table safety_event drop column location_source;