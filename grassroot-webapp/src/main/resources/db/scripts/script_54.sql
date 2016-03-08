do $$
declare
	gd record;
	myid bigint;
BEGIN
FOR gd IN
select g.id, g.uid from group_profile g where not exists (select gr.group_id from group_roles gr, role r where gr.group_id = g.id and gr.role_id = r.id and r.role_name = 'ROLE_GROUP_ORGANIZER')
LOOP
	INSERT INTO role (group_uid, role_type, role_name) VALUES (gd.uid, 'GROUP', 'ROLE_GROUP_ORGANIZER') returning id into myid;
	INSERT INTO group_roles (group_id, role_id) VALUES (gd.id, myid);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 17);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 28);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 18);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 23);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 27);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 22);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 20);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 13);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 16);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 24);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 29);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 26);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 19);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 21);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 30);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 25);

END LOOP;
end;
$$;

do $$
declare
	gd record;
	myid bigint;
BEGIN
FOR gd IN
select g.id, g.uid from group_profile g where not exists (select gr.group_id from group_roles gr, role r where gr.group_id = g.id and gr.role_id = r.id and r.role_name = 'ROLE_COMMITTEE_MEMBER')
LOOP
	INSERT INTO role (group_uid, role_type, role_name) VALUES (gd.uid, 'GROUP', 'ROLE_COMMITTEE_MEMBER') returning id into myid;
	INSERT INTO group_roles (group_id, role_id) VALUES (gd.id, myid);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 16);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 17);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 24);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 29);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 28);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 26);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 18);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 21);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 27);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 30);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 25);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 20);

END LOOP;
end;
$$;

do $$
declare
	gd record;
	myid bigint;
BEGIN
FOR gd IN
select g.id, g.uid from group_profile g where not exists (select gr.group_id from group_roles gr, role r where gr.group_id = g.id and gr.role_id = r.id and r.role_name = 'ROLE_ORDINARY_MEMBER')
LOOP
	INSERT INTO role (group_uid, role_type, role_name) VALUES (gd.uid, 'GROUP', 'ROLE_ORDINARY_MEMBER') returning id into myid;
	INSERT INTO group_roles (group_id, role_id) VALUES (gd.id, myid);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 24);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 29);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 28);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 26);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 27);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 30);
	INSERT INTO role_permissions (role_id, permission_id) VALUES (myid, 25);

END LOOP;
end;
$$;

ALTER TABLE group_user_membership ADD COLUMN id bigserial not null primary key;
ALTER TABLE group_user_membership ADD COLUMN role_id int8;
UPDATE group_user_membership m SET role_id = r.id FROM role r, group_profile g, user_roles ur where m.group_id = g.id and m.user_id = ur.user_id and ur.role_id = r.id and r.group_uid = g.uid;

update group_user_membership m SET role_id = gr.role_id from group_roles gr, role r, group_profile g where m.role_id is null and m.group_id = gr.group_id and gr.role_id = r.id and r.role_name = 'ROLE_GROUP_ORGANIZER' and gr.group_id = g.id and g.created_by_user = m.user_id;
update group_user_membership m SET role_id = gr.role_id from group_roles gr, role r where m.role_id is null and m.group_id = gr.group_id and gr.role_id = r.id and r.role_name = 'ROLE_ORDINARY_MEMBER';

ALTER TABLE group_user_membership ALTER COLUMN role_id SET NOT NULL;
alter table group_user_membership add constraint fk_membership_role foreign key (role_id) references role;
alter table group_user_membership add constraint uk_membership_group_user unique (group_id, user_id);
delete from user_roles ur where role_id in (select id from role where role_type = 'GROUP');

