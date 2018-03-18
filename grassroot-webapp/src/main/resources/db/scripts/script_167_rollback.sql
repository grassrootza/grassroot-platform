alter table user_profile drop column has_image;
alter table user_profile drop column contact_error;

delete from role_permissions where permission = 'GROUP_PERMISSION_SEND_BROADCAST';
delete from role_permissions where permission = 'GROUP_PERMISSION_CREATE_CAMPAIGN';