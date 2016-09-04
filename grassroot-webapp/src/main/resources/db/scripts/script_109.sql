UPDATE user_profile SET alert_preference = 'NOTIFY_NEW_AND_REMINDERS' WHERE alert_preference = 'NOTIFY_ALL_EVENTS';
UPDATE user_profile SET alert_preference = 'NOTIFY_NEW_AND_REMINDERS' WHERE alert_preference = 'NOTIFY_NEW_EVENTS';
UPDATE user_profile SET alert_preference = 'NOTIFY_EVERYTHING' WHERE alert_preference = 'NOTIFY_NEW_EVENTS_AND_GROUP_CHANGES';
UPDATE user_profile SET alert_preference = 'NOTIFY_NEW_AND_REMINDERS' WHERE alert_preference = 'NOTIFY_GROUP_CHANGES';

ALTER TABLE action_todo_completion_confirmation RENAME COLUMN log_book_id TO action_todo_id;
ALTER TABLE event RENAME COLUMN parent_log_book_id TO parent_action_todo_id;
ALTER TABLE event_request RENAME COLUMN parent_log_book_id TO parent_action_todo_id;
ALTER TABLE notification RENAME COLUMN log_book_id TO action_todo_id;
ALTER TABLE notification RENAME COLUMN log_book_log_id TO action_todo_log_id;
ALTER TABLE action_todo_log RENAME COLUMN log_book_id TO action_todo_id;
ALTER TABLE action_todo_assigned_members RENAME COLUMN log_book_id TO action_todo_id;
ALTER TABLE action_todo_request_assigned_members RENAME COLUMN log_book_request_id TO action_todo_request_id;

UPDATE notification SET type = 'TODO_INFO' WHERE type = 'LOG_BOOK_INFO';
UPDATE notification SET type = 'TODO_REMINDER' WHERE type = 'LOG_BOOK_REMINDER';

ALTER TABLE action_todo_completion_confirmation RENAME CONSTRAINT fk_log_book_compl_confirm_log_book TO fk_action_todo_compl_confirm_action_todo;
ALTER TABLE action_todo_completion_confirmation RENAME CONSTRAINT fk_log_book_compl_confirm_member TO fk_action_todo_compl_confirm_member;

ALTER TABLE action_todo_assigned_members RENAME CONSTRAINT fk_log_book_assigned_book TO fk_action_todo_assigned_todo;
ALTER TABLE action_todo_assigned_members RENAME CONSTRAINT fk_log_book_assigned_user TO fk_action_todo_assigned_user;

ALTER TABLE action_todo RENAME CONSTRAINT fk_log_book_ancestor_group TO fk_action_todo_ancestor_group;
ALTER TABLE action_todo RENAME CONSTRAINT fk_log_book_parent_event TO fk_action_todo_parent_event;

ALTER TABLE action_todo_request RENAME CONSTRAINT fk_log_book_req_parent_event TO fk_action_todo_req_parent_event;
ALTER TABLE action_todo_request RENAME CONSTRAINT fk_log_book_request_created_by_user TO fk_action_todo_request_created_by_user;
ALTER TABLE action_todo_request RENAME CONSTRAINT fk_log_book_request_group TO fk_action_todo_request_group;

ALTER TABLE action_todo_request_assigned_members RENAME CONSTRAINT fk_log_book_request_assigned_request TO fk_action_todo_request_assigned_request;
ALTER TABLE action_todo_request_assigned_members RENAME CONSTRAINT fk_log_book_request_assigned_user TO fk_action_todo_request_assigned_user;

ALTER TABLE notification RENAME CONSTRAINT fk_log_book TO fk_account_todo;
ALTER TABLE notification RENAME CONSTRAINT fk_log_book__log_not144107 TO fk_action_todo__log_not144107;

ALTER TABLE event RENAME CONSTRAINT fk_event_parent_log_book TO fk_event_parent_action_todo;
ALTER TABLE event_request RENAME CONSTRAINT fk_event_req_parent_log_book TO fk_event_req_parent_action_todo;

ALTER TABLE paid_account RENAME COLUMN logbook_extra TO action_todo_extra;