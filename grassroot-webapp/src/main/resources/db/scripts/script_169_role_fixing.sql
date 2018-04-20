insert into user_roles (user_id, role_id) select
  id, (select id from role where role_name = 'ROLE_NEW_USER') from user_profile
  where initiated_session = true and
        id not in (select user_id from user_roles where role_id = (select id from role where role_name = 'ROLE_NEW_USER'));

update role set role_name = 'ROLE_FULL_USER' where role_name = 'ROLE_NEW_USER';