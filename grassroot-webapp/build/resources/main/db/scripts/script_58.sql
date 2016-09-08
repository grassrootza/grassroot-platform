ALTER TABLE group_user_membership ADD COLUMN join_time timestamp;
UPDATE group_user_membership SET join_time = current_timestamp;
ALTER TABLE group_user_membership ALTER COLUMN join_time SET NOT NULL;
