ALTER TABLE group_user_membership ADD COLUMN id bigserial not null primary key;
ALTER TABLE group_user_membership ADD COLUMN role_id int8;
UPDATE group_user_membership m SET role_id = r.id FROM role r, group_profile g, user_roles ur where m.group_id = g.id and m.user_id = ur.user_id and ur.role_id = r.id and r.group_uid = g.uid;
ALTER TABLE group_user_membership ALTER COLUMN role_id SET NOT NULL;
alter table group_user_membership add constraint fk_membership_role foreign key (role_id) references role;
alter table group_user_membership add constraint uk_membership_group_user unique (group_id, user_id);
delete from user_roles ur where role_id in (select id from role where role_type = 'GROUP');

