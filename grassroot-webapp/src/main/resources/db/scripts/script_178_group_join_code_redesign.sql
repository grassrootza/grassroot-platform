-- indices on foreign key columns since PostgreSQL doesn't add it automatically
create index idx_membership_group on group_user_membership(group_id);
create index idx_membership_user on group_user_membership(user_id);

-- group_join_code redesign
alter table group_join_code add column group_id bigint;
alter table group_join_code add column user_id bigint;
alter table group_join_code add column closing_user_id bigint;

alter table group_join_code add constraint fk_join_code_group foreign key (group_id) references group_profile;
alter table group_join_code add constraint fk_join_code_user foreign key (user_id) references user_profile;
alter table group_join_code add constraint fk_join_code_closing_user foreign key (closing_user_id) references user_profile;
alter table group_join_code add constraint pk_join_code primary key (id);

update group_join_code set group_id = g.id from group_profile g where group_uid = g.uid;
update group_join_code set user_id = u.id from user_profile u where user_uid = u.uid;
update group_join_code set closing_user_id = u.id from user_profile u where closing_user_uid = u.uid;

alter table group_join_code alter column group_id set not null;
alter table group_join_code alter column user_id set not null;
alter table group_join_code alter column code set not null;


alter table group_join_code drop column group_uid;
alter table group_join_code drop column user_uid;
alter table group_join_code drop column closing_user_uid;
alter table group_join_code drop column type;
alter table group_join_code drop column version;
