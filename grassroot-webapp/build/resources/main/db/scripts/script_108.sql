ALTER TABLE action_todo ADD COLUMN version INTEGER DEFAULT 0;
ALTER TABLE action_todo_request ADD COLUMN version INTEGER DEFAULT 0;

ALTER SEQUENCE log_book_id_seq RENAME TO action_todo_id_seq;
ALTER SEQUENCE log_book_log_id_seq RENAME TO action_todo_log_id_seq;
ALTER SEQUENCE log_book_request_id_seq RENAME TO action_todo_request_id_seq;
ALTER SEQUENCE log_book_completion_confirmation_id_seq RENAME TO action_todo_completion_confirmation_id_seq;

ALTER INDEX IF EXISTS log_book_pkey RENAME TO action_todo_pkey;
ALTER INDEX IF EXISTS idx_log_book_group_id RENAME TO idx_action_todo_group_id;
ALTER INDEX IF EXISTS idx_log_book_replicated_group_id RENAME TO idx_action_todo_replicated_group_id;
ALTER INDEX IF EXISTS idx_log_book_retries_left RENAME TO idx_action_todo_retries_left;
ALTER INDEX IF EXISTS log_book_log_pkey RENAME TO idx_action_todo_log_pkey;
ALTER INDEX IF EXISTS idx_log_book_log_logbook_id RENAME TO idx_action_todo_log_actiontodo_id;
ALTER INDEX IF EXISTS uk_log_book_uid RENAME TO uk_action_todo_uid;
ALTER INDEX IF EXISTS log_book_request_pkey RENAME TO action_todo_request_pkey;
ALTER INDEX IF EXISTS uk_log_book_request_uid RENAME TO uk_action_todo_request_uid;
ALTER INDEX IF EXISTS log_book_assigned_members_pkey RENAME TO action_todo_assigned_members_pkey;
ALTER INDEX IF EXISTS log_book_request_assigned_members_pkey RENAME TO action_todo_request_assigned_members_pkey;
ALTER INDEX IF EXISTS log_book_completion_confirmation_pkey RENAME TO action_todo_completion_confirmation_pkey;
ALTER INDEX IF EXISTS uk_compl_confirmation_log_book_member RENAME TO uk_compl_confirmation_action_todo_member;
ALTER INDEX IF EXISTS log_book_message_fts_idx RENAME TO action_todo_message_fts_idx;
