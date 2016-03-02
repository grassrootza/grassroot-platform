ALTER TABLE role DROP COLUMN group_reference_name;
ALTER TABLE role ADD COLUMN group_uid varchar(50);
UPDATE role SET group_uid = g.uid FROM group_profile AS g WHERE g.id = group_reference_id;
ALTER TABLE role DROP COLUMN group_reference_id;
