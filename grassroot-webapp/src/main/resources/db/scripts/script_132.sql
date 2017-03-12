alter table user_profile add column free_trial_used boolean default false not null;
alter table event_log add column latitude double precision;
alter table event_log add column longitude double precision;
alter table action_todo_log add column latitude double precision;
alter table action_todo_log add column longitude double precision;

ALTER TABLE action_todo_log ADD COLUMN uid varchar(50);
UPDATE action_todo_log SET uid = 'auto_' || nextval('action_todo_log_id_seq');
ALTER TABLE action_todo_log ALTER COLUMN uid SET NOT NULL;
ALTER TABLE ONLY action_todo_log ADD CONSTRAINT uk_todolog_uid UNIQUE (uid);
