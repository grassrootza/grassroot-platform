UPDATE event_log SET event_log_type = 'MANUAL_REMINDER' WHERE event_log_type = 'FREE_FORM_MESSAGE';
DELETE FROM event_log WHERE event_log_type = 'MINUTES'; -- just in case some got in
