ALTER TABLE log_book ADD COLUMN scheduled_reminder_time TIMESTAMP;
ALTER TABLE log_book ADD COLUMN reminder_active BOOLEAN DEFAULT FALSE;
UPDATE log_book SET reminder_active = TRUE;

ALTER TABLE log_book_request ADD COLUMN scheduled_reminder_time TIMESTAMP;
ALTER TABLE log_book_request ADD COLUMN reminder_active BOOLEAN DEFAULT FALSE;
UPDATE log_book_request SET reminder_active = TRUE;

ALTER TABLE log_book ADD COLUMN cancelled BOOLEAN DEFAULT FALSE;

ALTER TABLE log_book RENAME TO action_todo;
ALTER TABLE log_book_assigned_members RENAME TO action_todo_assigned_members;
ALTER TABLE log_book_completion_confirmation RENAME TO action_todo_completion_confirmation;
ALTER TABLE log_book_log RENAME TO action_todo_log;
ALTER TABLE log_book_request RENAME TO action_todo_request;
ALTER TABLE log_book_request_assigned_members RENAME TO action_todo_request_assigned_members;
