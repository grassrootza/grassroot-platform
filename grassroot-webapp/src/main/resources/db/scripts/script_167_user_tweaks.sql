alter table user_profile add column has_image boolean default false;
update user_profile set has_image = false;

alter table user_profile add column contact_error boolean default false;
update user_profile set contact_error = false;

insert into role_permissions(role_id, permission)
  select role_id, 'GROUP_PERMISSION_SEND_BROADCAST'
  from role_permissions
  where permission = 'GROUP_PERMISSION_UPDATE_GROUP_DETAILS';

insert into role_permissions(role_id, permission)
  select role_id, 'GROUP_PERMISSION_CREATE_CAMPAIGN'
  from role_permissions
  where permission = 'GROUP_PERMISSION_UPDATE_GROUP_DETAILS';