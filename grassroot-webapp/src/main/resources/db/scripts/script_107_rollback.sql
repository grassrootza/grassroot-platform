ALTER TABLE action_todo_request_assigned_members RENAME TO log_book_request_assigned_members ;
ALTER TABLE action_todo_request RENAME TO log_book_request;
ALTER TABLE action_todo_log RENAME TO log_book_log;
ALTER TABLE action_todo_completion_confirmation RENAME TO log_book_completion_confirmation;
ALTER TABLE action_todo_assigned_members RENAME TO log_book_assigned_members;
ALTER TABLE action_todo RENAME TO log_book;

ALTER TABLE log_book DROP COLUMN scheduled_reminder_time;
ALTER TABLE log_book DROP COLUMN reminder_active;
ALTER TABLE log_book_request DROP COLUMN scheduled_reminder_time;
ALTER TABLE log_book_request DROP COLUMN reminder_active;

ALTER TABLE log_book DROP COLUMN cancelled;