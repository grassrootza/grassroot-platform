alter table user_profile drop column free_trial_used;
alter table event_log drop column latitude;
alter table event_log drop column longitude;
alter table action_todo_log drop column latitude;
alter table action_todo_log drop column longitude;

alter table action_todo_log drop column uid;