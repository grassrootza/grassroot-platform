create table user_standard_role (
user_id bigint not null,
role varchar(100) not null
);

alter table user_standard_role add constraint fk_user_standrd_role_user foreign key (user_id) references user_profile;
alter table user_standard_role add constraint uk_user_standard_role unique (user_id, role);

insert into user_standard_role (user_id, role)
select ur.user_id, r.role_name
from user_roles ur
inner join role r on ur.role_id = r.id;

create table group_role_permission (
group_id bigint not null,
role varchar(50) not null,
permission varchar(100) not null
);
alter table group_role_permission add constraint fk_group_role_permission_group foreign key (group_id) references group_profile;
alter table group_role_permission add constraint uk_group_role_permission unique (group_id, role, permission);

insert into group_role_permission (group_id, role, permission)
select gr.group_id, r.role_name, rp.permission
from group_roles gr inner join role r on r.id = gr.role_id inner join role_permissions rp on r.id = rp.role_id;

alter table group_user_membership add column role varchar(50);
update group_user_membership set role = r.role_name from role r where role_id = r.id;
alter table group_user_membership alter column role set not null;

drop table user_roles;
drop table group_roles;
drop table role_permissions;
alter table group_user_membership drop column role_id;
drop table role;
