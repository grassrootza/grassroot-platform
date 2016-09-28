ALTER TABLE action_todo ADD COLUMN source_todo bigint;
ALTER TABLE action_todo DROP COLUMN replicated_group_id;