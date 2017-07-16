alter table group_user_membership add column alias varchar(50);

-- alter table user_profile add column aliases text[] default '{}';
-- update user_profile set aliases = '{}';

-- create index user_aliases on user_profile using gin(aliases);