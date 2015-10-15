ALTER TABLE user_profile ADD COLUMN initiated_session boolean;
ALTER TABLE user_profile ALTER COLUMN initiated_session SET DEFAULT FALSE;
update user_profile set initiated_session = false;