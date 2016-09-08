ALTER TABLE user_profile RENAME COLUMN uid TO app_id;
ALTER TABLE user_profile RENAME CONSTRAINT uk_user_uid TO uk_user_app_id;


