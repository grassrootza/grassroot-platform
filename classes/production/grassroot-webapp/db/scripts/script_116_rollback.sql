ALTER TABLE paid_account DROP COLUMN max_group_number;
ALTER TABLE paid_account DROP COLUMN max_group_size;
ALTER TABLE paid_account DROP COLUMN max_sub_gorup_depth;
ALTER TABLE paid_account DROP COLUMN additional_reminders;

ALTER TABLE paid_account DROP CONSTRAINT fk_disabled_by_user;

ALTER TABLE paid_account DROP COLUMN disabled_by_user;
ALTER TABLE paid_account DROP COLUMN disabled_date_time;

ALTER TABLE paid_account ADD COLUMN relayable boolean;
ALTER TABLE paid_account ADD COLUMN action_todo_extra boolean;

ALTER TABLE action_todo DROP CONSTRAINT fk_source_todo;