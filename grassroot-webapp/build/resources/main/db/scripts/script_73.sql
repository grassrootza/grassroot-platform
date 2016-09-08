ALTER TABLE user_log ADD COLUMN user_interface integer;
UPDATE user_log SET user_interface = 0;
ALTER TABLE user_log ALTER COLUMN user_interface SET NOT NULL;