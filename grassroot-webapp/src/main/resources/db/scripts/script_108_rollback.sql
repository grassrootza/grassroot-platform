ALTER SEQUENCE action_todo_id_seq RENAME TO log_book_id_seq;
ALTER SEQUENCE action_todo_log_id_seq RENAME TO log_book_log_id_seq;
ALTER SEQUENCE action_todo_request_id_seq RENAME TO log_book_request_id_seq;
ALTER SEQUENCE action_todo_completion_confirmation_id_seq RENAME TO log_book_completion_confirmation_id_seq;

ALTER TABLE action_todo DROP COLUMN version;

-- Indices too complex and not much point