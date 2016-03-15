do $$
declare
  gid bigint;
  roleid bigint;
BEGIN
  FOR gid IN
  select g.id from group_profile g where not exists (select m.group_id from group_user_membership m, role r where g.id = m.group_id and m.role_id = r.id and r.role_name = 'ROLE_GROUP_ORGANIZER')
  LOOP
    SELECT r.id INTO roleid FROM group_roles gr, role r WHERE gr.role_id = r.id AND r.role_name = 'ROLE_GROUP_ORGANIZER' and gr.group_id = gid;
    UPDATE group_user_membership SET role_id = roleid WHERE group_id = gid;

  END LOOP;
end;
$$;