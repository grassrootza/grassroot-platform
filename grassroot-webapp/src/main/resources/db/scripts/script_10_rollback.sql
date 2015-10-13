
ALTER TABLE role DROP CONSTRAINT unique_role_name;
ALTER TABLE role DROP group_reference_id;
ALTER TABLE role DROP group_reference_name;
ALTER TABLE role DROP role_type;


ALTER TABLE permission DROP CONSTRAINT unique_mask;
ALTER TABLE permission DROP CONSTRAINT unique_permission_name;

ALTER TABLE permission DROP mask;



