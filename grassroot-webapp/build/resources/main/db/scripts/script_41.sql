INSERT INTO group_log (created_date_time, group_id, group_log_type, user_id, user_or_sub_group_id, description)
  select created_date_time,id,0, created_by_user,0,'Imported from active groups'
  from group_profile where active = TRUE;
INSERT INTO group_log (created_date_time, group_id, group_log_type, user_id, user_or_sub_group_id, description)
  select current_timestamp,group_id,4, 0,user_id,'Imported current members'
  from group_user_membership;