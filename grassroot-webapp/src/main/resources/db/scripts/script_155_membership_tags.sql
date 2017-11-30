alter table group_user_membership add column tags text[] default '{}';
update group_user_membership set tags = '{}';
create index member_tag_index on group_user_membership using gin(tags);