delete from group_log where user_or_sub_group_id = 0;

update group_log set group_log_type = 'SUBGROUP_ADDED', description = 'Subgroup added' WHERE group_log_type = 'GROUP_ADDED' and user_or_sub_group_id is not null;
update group_log set group_log_type = 'SUBGROUP_REMOVED', description = 'Subgroup removed' WHERE group_log_type = 'GROUP_REMOVED' and user_or_sub_group_id is not null;

do $$
declare
  grec record;
BEGIN
  FOR grec IN
  select * from group_profile g where g.id not in (select distinct gl.group_id from group_log gl where group_log_type = 'GROUP_ADDED')
  LOOP
    insert into group_log (created_date_time, group_id, user_id, group_log_type, description)
    values (grec.created_date_time, grec.id, grec.created_by_user, 'GROUP_ADDED', 'Group added');
  END LOOP;
end;
$$;
