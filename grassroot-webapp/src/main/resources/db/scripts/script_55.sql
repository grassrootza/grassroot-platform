ALTER TABLE role ALTER COLUMN role_type SET NOT NULL;
ALTER TABLE role_permissions ADD COLUMN permission varchar(50);
UPDATE role_permissions SET permission = pt.permission_name FROM permission pt WHERE pt.id = permission_id;
ALTER TABLE role_permissions ALTER COLUMN permission SET NOT NULL;
ALTER TABLE role_permissions DROP COLUMN permission_id;
DROP TABLE permission;




