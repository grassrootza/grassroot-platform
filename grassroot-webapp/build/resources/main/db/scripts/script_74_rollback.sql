ALTER TABLE user_profile DROP COLUMN message_preference;
ALTER TABLE event_log DROP COLUMN uid;
ALTER TABLE event_log DROP COLUMN message_default;

ALTER TABLE user_profile ADD COLUMN last_ussd_menu VARCHAR(100);