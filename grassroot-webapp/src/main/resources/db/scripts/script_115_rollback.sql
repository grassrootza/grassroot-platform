ALTER TABLE action_todo DROP COLUMN source_todo;
ALTER TABLE action_todo ADD COLUMN replicated_group_id bigint;