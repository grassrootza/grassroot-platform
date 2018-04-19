update role set role_name = 'ROLE_NEW_USER' where role_name = 'ROLE_FULL_USER';

delete from user_roles where role_id = (select id from role where role_name = 'ROLE_NEW_USER');
