ALTER TABLE action_todo_completion_confirmation ADD COLUMN creation_time timestamp;
UPDATE action_todo_completion_confirmation SET creation_time = completion_time;
UPDATE action_todo_completion_confirmation SET creation_time = current_timestamp WHERE creation_time IS NULL;
ALTER TABLE action_todo_completion_confirmation ALTER COLUMN creation_time SET NOT NULL;

ALTER TABLE action_todo_completion_confirmation ADD COLUMN confirmation_type VARCHAR(50);
UPDATE action_todo_completion_confirmation SET confirmation_type = 'COMPLETED';
ALTER TABLE action_todo_completion_confirmation ALTER COLUMN confirmation_type SET NOT NULL;