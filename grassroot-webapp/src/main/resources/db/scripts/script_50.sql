ALTER TABLE user_profile RENAME COLUMN app_id TO uid;
ALTER TABLE user_profile RENAME CONSTRAINT uk_user_app_id TO uk_user_uid;


