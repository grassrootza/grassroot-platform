alter table action_todo drop column source_todo;
alter table action_todo drop column completed_date;

drop index idx_action_todo_retries_left;
alter table action_todo drop column number_of_reminders_left_to_send;

alter table action_todo rename column scheduled_reminder_time to next_notification_time;

-- not dropping completion_percentage as may contain historical info and is just a column
alter table action_todo add column completed boolean default false;
update action_todo set completed = true;

alter table action_todo add column todo_type varchar(50);
update action_todo set todo_type = 'ACTION_REQUIRED';

alter table action_todo alter column todo_type set not null;
create index index_action_todo_type on action_todo (todo_type);

alter table action_todo add column response_tag varchar(255);
alter table action_todo add column response_regex varchar(50);
alter table action_todo add column require_images boolean default false;
alter table action_todo add column allow_simple boolean default false;
alter table action_todo add column recurring boolean default false;
alter table action_todo add column recurring_interval bigint;

alter table action_todo_request add column todo_type varchar(50);
update action_todo_request set todo_type = 'ACTION_REQUIRED';
alter table action_todo_request alter column todo_type set not null;

alter table action_todo_request add column response_tag varchar(255);
alter table action_todo_request alter column parent_group_id drop not null;
alter table action_todo_request drop column replicate_to_subgroups;

-- drop table action_todo_request_assigned_members; (do right at end as rollback painful)

alter table action_todo_assigned_members drop constraint action_todo_assigned_members_pkey;
alter table action_todo_assigned_members add column id bigserial not null primary key;
alter table action_todo_assigned_members add constraint uk_todo_user_assignment unique (action_todo_id, user_id);

alter table action_todo_assigned_members add column creation_time timestamp;
update action_todo_assigned_members set creation_time = current_timestamp;
alter table action_todo_assigned_members alter column creation_time set not null;

alter table action_todo_assigned_members add column assigned_action boolean default false;
alter table action_todo_assigned_members add column assigned_witness boolean default false;

alter table action_todo_assigned_members add column should_respond boolean default false;
alter table action_todo_assigned_members add column has_responded boolean default false;
alter table action_todo_assigned_members add column response_date_time timestamp;
alter table action_todo_assigned_members add column confirmation_type varchar(50);
alter table action_todo_assigned_members add column response_text varchar(255);

-- need the following, though not completely related
alter table event alter column location type varchar(255);