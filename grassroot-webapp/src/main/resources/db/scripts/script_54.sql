do $$
declare
	gd record;
	myid bigint;
BEGIN
FOR gd IN
	select g.id, g.uid from group_profile g where not exists (select gr.group_id from group_roles gr where gr.group_id = g.id)
LOOP
	INSERT INTO role (group_uid, role_type, role_name) VALUES (gd.uid, 'GROUP', 'ROLE_COMMITTEE_MEMBER') returning id into myid;
	INSERT INTO group_roles (group_id, role_id) VALUES (gd.id, myid);
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_CREATE_SUBGROUP');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_AUTHORIZE_SUBGROUP');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_SEE_MEMBER_DETAILS');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_READ_UPCOMING_EVENTS');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_VIEW_MEETING_RSVPS');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_DELEGATE_SUBGROUP_CREATION');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_FORCE_ADD_MEMBER');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_CREATE_GROUP_VOTE');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_CLOSE_OPEN_LOGBOOK');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_CREATE_GROUP_MEETING');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_ADD_GROUP_MEMBER');

	INSERT INTO role (group_uid, role_type, role_name) VALUES (gd.uid, 'GROUP', 'ROLE_ORDINARY_MEMBER') returning id into myid;
	INSERT INTO group_roles (group_id, role_id) VALUES (gd.id, myid);
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_SEE_MEMBER_DETAILS');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_READ_UPCOMING_EVENTS');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_VIEW_MEETING_RSVPS');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_CREATE_GROUP_VOTE');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_CLOSE_OPEN_LOGBOOK');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_CREATE_GROUP_MEETING');

	INSERT INTO role (group_uid, role_type, role_name) VALUES (gd.uid, 'GROUP', 'ROLE_GROUP_ORGANIZER') returning id into myid;
	INSERT INTO group_roles (group_id, role_id) VALUES (gd.id, myid);
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_AUTHORIZE_SUBGROUP');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_READ_UPCOMING_EVENTS');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_DELEGATE_SUBGROUP_CREATION');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_FORCE_DELETE_MEMBER');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_CREATE_GROUP_VOTE');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_DELETE_GROUP_MEMBER');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_ADD_GROUP_MEMBER');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_UPDATE_GROUP_DETAILS');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_CREATE_SUBGROUP');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_SEE_MEMBER_DETAILS');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_VIEW_MEETING_RSVPS');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_DELINK_SUBGROUP');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_FORCE_ADD_MEMBER');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_CLOSE_OPEN_LOGBOOK');
	INSERT INTO role_permissions (role_id, permission) VALUES (myid, 'GROUP_PERMISSION_CREATE_GROUP_MEETING');

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

