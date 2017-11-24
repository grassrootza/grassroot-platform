alter table action_todo add column source_todo bigint;
alter table action_todo add column number_of_reminders_left_to_send int default 0;
create index idx_action_todo_retries_left on action_todo(number_of_reminders_left_to_send);

alter table action_todo rename column next_notification_time to scheduled_reminder_time;

alter table action_todo drop column completed;
alter table action_todo add column completed_date timestamp;

drop index index_action_todo_type;
alter table action_todo drop column todo_type;

alter table action_todo drop column response_tag;
alter table action_todo drop column response_regex;
alter table action_todo drop column require_images;
alter table action_todo drop column allow_simple;
alter table action_todo drop column recurring;
alter table action_todo drop column recurring_interval;

alter table action_todo_request drop column todo_type;
alter table action_todo_request drop column response_tag;

alter table action_todo_request add column replicate_to_subgroups boolean default false;

alter table action_todo_assigned_members drop column id;
alter table action_todo_assigned_members drop constraint uk_todo_user_assignment;
alter table action_todo_assigned_members add primary key (action_todo_id, user_id);

alter table action_todo_assigned_members drop column creation_time;
alter table action_todo_assigned_members drop column assigned_action;
alter table action_todo_assigned_members drop column assigned_witness;

alter table action_todo_assigned_members drop column should_respond;
alter table action_todo_assigned_members drop column has_responded;
alter table action_todo_assigned_members drop column witness_time;
alter table action_todo_assigned_members drop column confirmation_type;
alter table action_todo_assigned_members drop column response_text;
alter table action_todo_assigned_members drop column response_date_time;

alter table event alter column location type varchar(50);