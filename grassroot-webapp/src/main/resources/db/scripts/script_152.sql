alter table action_todo add column description VARCHAR(512);
alter table action_todo_request add column description VARCHAR(512);

alter table action_todo add column public boolean default false;
alter table action_todo_request add column public boolean default false;

alter table event add column public boolean default false;
alter table event_request add column public boolean default false;