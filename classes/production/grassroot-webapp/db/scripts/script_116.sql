ALTER TABLE paid_account ADD COLUMN max_group_number INTEGER DEFAULT 0;
ALTER TABLE paid_account ADD COLUMN max_group_size INTEGER DEFAULT 0;
ALTER TABLE paid_account ADD COLUMN max_sub_group_depth INTEGER DEFAULT 0;
ALTER TABLE paid_account ADD COLUMN additional_reminders INTEGER DEFAULT 0;

ALTER TABLE paid_account ADD COLUMN disabled_by_user BIGINT;
ALTER TABLE paid_account ADD COLUMN disabled_date_time TIMESTAMP;

ALTER TABLE paid_account DROP COLUMN relayable;
ALTER TABLE paid_account DROP COLUMN action_todo_extra;

ALTER TABLE paid_account ADD CONSTRAINT fk_disabled_by_user FOREIGN KEY (disabled_by_user) REFERENCES user_profile;
ALTER TABLE action_todo ADD CONSTRAINT fk_source_todo FOREIGN KEY (source_todo) references action_todo;

