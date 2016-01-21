ALTER TABLE group_profile ADD COLUMN discoverable boolean DEFAULT false;
UPDATE group_profile SET discoverable = false;