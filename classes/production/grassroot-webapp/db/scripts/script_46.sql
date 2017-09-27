INSERT INTO user_log (created_date_time, user_id, user_log_type, description)
  select created_date_time, id, 0, 'Set from existing user'
  from user_profile where created_date_time IS NOT NULL;
INSERT INTO user_log (created_date_time, user_id, user_log_type, description)
  select current_timestamp, id, 1, 'Initiated user session earlier than now'
  from user_profile where initiated_session = TRUE;
INSERT INTO user_log (created_date_time, user_id, user_log_type, description)
  select current_timestamp, id, 2, 'Created web profile earlier than now'
  from user_profile where web = TRUE;
