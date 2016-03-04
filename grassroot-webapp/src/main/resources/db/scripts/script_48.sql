ALTER TABLE user_profile ADD COLUMN app_id varchar(50);
UPDATE user_profile SET app_id = 'auto_' || nextval('user_profile_id_seq');
ALTER TABLE user_profile ALTER COLUMN app_id SET NOT NULL;
ALTER TABLE ONLY user_profile ADD CONSTRAINT uk_user_app_id UNIQUE (app_id);