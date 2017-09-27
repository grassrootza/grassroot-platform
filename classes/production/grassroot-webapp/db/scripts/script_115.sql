ALTER TABLE action_todo ADD COLUMN source_todo bigint;
ALTER TABLE action_todo DROP COLUMN replicated_group_id;

ALTER TABLE user_profile ADD COLUMN has_set_name boolean DEFAULT FALSE;
UPDATE user_profile SET has_set_name = TRUE WHERE display_name IS NOT NULL;