<<<<<<< HEAD
ALTER TABLE role ADD COLUMN group_reference_id bigint;
ALTER TABLE role ADD COLUMN group_reference_name VARCHAR(100) NULL;
ALTER TABLE role ADD COLUMN role_type VARCHAR(50) NULL;
ALTER TABLE role ALTER COLUMN role_name SET NOT NULL;
ALTER TABLE role ADD CONSTRAINT unique_role_name UNIQUE (role_name);


ALTER TABLE permission ADD mask INT DEFAULT 0 NOT NULL;
ALTER TABLE permission ADD CONSTRAINT unique_mask UNIQUE (mask);
ALTER TABLE permission ADD CONSTRAINT unique_permission_name UNIQUE (permission_name);
=======
ALTER TABLE event ADD COLUMN can_relay boolean;
ALTER TABLE event ALTER COLUMN can_relay SET DEFAULT FALSE;
update event set can_relay = false;
>>>>>>> origin/master
